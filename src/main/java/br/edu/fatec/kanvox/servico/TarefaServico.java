package br.edu.fatec.kanvox.servico;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.fatec.kanvox.modelo.MembroProjeto;
import br.edu.fatec.kanvox.modelo.PapelProjeto;
import br.edu.fatec.kanvox.modelo.Projeto;
import br.edu.fatec.kanvox.modelo.StatusProjeto;
import br.edu.fatec.kanvox.modelo.StatusTarefa;
import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.modelo.TipoNotificacao;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.MembroProjetoRepositorio;
import br.edu.fatec.kanvox.repositorio.TarefaRepositorio;

/**
 * Regras de negocio do quadro Kanban (RF-03).
 * Permissoes por papel, validadas aqui na camada de servico (RNF-02):
 * - Gestor: cria, edita o conteudo, move e exclui qualquer tarefa do projeto (RF-03.2)
 * - Membro: apenas movimenta (drag-and-drop / mudanca de status) as tarefas
 *   atribuidas a ele (RF-03.3) — nao cria nem edita titulo/descricao/prazo/
 *   prioridade/responsaveis; colabora via comentarios e anexos (RF-07)
 * - Observador: somente leitura
 * Toda acao relevante (criar, editar, mover de coluna) fica registrada na
 * linha do tempo da tarefa via HistoricoTarefaServico.
 */
@Service
@Transactional
public class TarefaServico {

	private final TarefaRepositorio tarefaRepositorio;
	private final MembroProjetoRepositorio membroProjetoRepositorio;
	private final ProjetoServico projetoServico;
	private final NotificacaoServico notificacaoServico;
	private final HistoricoTarefaServico historicoTarefaServico;

	public TarefaServico(TarefaRepositorio tarefaRepositorio,
			MembroProjetoRepositorio membroProjetoRepositorio,
			ProjetoServico projetoServico,
			NotificacaoServico notificacaoServico,
			HistoricoTarefaServico historicoTarefaServico) {
		this.tarefaRepositorio = tarefaRepositorio;
		this.membroProjetoRepositorio = membroProjetoRepositorio;
		this.projetoServico = projetoServico;
		this.notificacaoServico = notificacaoServico;
		this.historicoTarefaServico = historicoTarefaServico;
	}

	/**
	 * Lista as tarefas do projeto — qualquer membro ativo, inclusive Observador.
	 * E este endpoint que o frontend consulta no polling automatico (RF-03.6).
	 */
	public List<Tarefa> listarPorProjeto(Usuario usuarioLogado, Long projetoId) {
		projetoServico.buscarVinculoObrigatorio(projetoId, usuarioLogado);
		return tarefaRepositorio.buscarPorProjeto(projetoId);
	}

	/**
	 * Cria uma tarefa — operacao exclusiva do Gestor (RF-03.2), que pode
	 * atribui-la a varios membros ativos ou deixa-la sem responsavel.
	 * Toda tarefa nasce na coluna "A Fazer" (RF-03.1).
	 */
	public Tarefa criar(Usuario usuarioLogado, Long projetoId, Tarefa dadosRecebidos) {
		MembroProjeto vinculoDoCriador = projetoServico.buscarVinculoObrigatorio(projetoId, usuarioLogado);
		validarProjetoAtivo(vinculoDoCriador.getProjeto());
		if (vinculoDoCriador.getPapelNoProjeto() != PapelProjeto.GESTOR) {
			throw new PermissaoNegadaExcecao("Somente o Gestor do projeto pode criar tarefas.");
		}
		if (dadosRecebidos.getTitulo() == null || dadosRecebidos.getTitulo().isBlank()) {
			throw new RegraDeNegocioExcecao("O titulo da tarefa e obrigatorio.");
		}

		Tarefa novaTarefa = new Tarefa();
		novaTarefa.setProjeto(vinculoDoCriador.getProjeto());
		novaTarefa.setTitulo(dadosRecebidos.getTitulo());
		novaTarefa.setDescricao(dadosRecebidos.getDescricao());
		novaTarefa.setPrazo(dadosRecebidos.getPrazo());
		if (dadosRecebidos.getPrioridade() != null) {
			novaTarefa.setPrioridade(dadosRecebidos.getPrioridade());
		}
		novaTarefa.setResponsaveis(validarResponsaveis(projetoId, dadosRecebidos.getResponsaveis()));
		novaTarefa.setDependencias(validarDependencias(projetoId, null, dadosRecebidos.getDependencias()));
		Tarefa tarefaSalva = tarefaRepositorio.save(novaTarefa);
		notificarAtribuicao(tarefaSalva, tarefaSalva.getResponsaveis(), usuarioLogado);
		historicoTarefaServico.registrar(tarefaSalva, usuarioLogado, "Criou a tarefa.");
		return tarefaSalva;
	}

	/**
	 * Edita titulo, descricao, prazo, prioridade e responsaveis da tarefa.
	 * Editar o CONTEUDO da tarefa e exclusivo do Gestor (RF-03.2) — o Membro
	 * so movimenta a propria tarefa entre colunas (ver moverStatus) e
	 * colabora via comentarios/anexos (RF-07), nunca edita os dados dela.
	 */
	public Tarefa editar(Usuario usuarioLogado, Long tarefaId, Tarefa dadosRecebidos) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		projetoServico.validarGestor(tarefa.getProjeto().getId(), usuarioLogado);
		validarProjetoAtivo(tarefa.getProjeto());
		if (dadosRecebidos.getTitulo() == null || dadosRecebidos.getTitulo().isBlank()) {
			throw new RegraDeNegocioExcecao("O titulo da tarefa e obrigatorio.");
		}

		List<Usuario> novosResponsaveis = validarResponsaveis(tarefa.getProjeto().getId(),
				dadosRecebidos.getResponsaveis());
		List<Usuario> responsaveisAdicionados = novosResponsaveis.stream()
				.filter(usuario -> tarefa.getResponsaveis().stream()
						.noneMatch(atual -> atual.getId().equals(usuario.getId())))
				.toList();

		List<Tarefa> novasDependencias = validarDependencias(tarefa.getProjeto().getId(), tarefa.getId(),
				dadosRecebidos.getDependencias());
		validarSemCiclo(tarefa, novasDependencias);

		// monta o resumo do que mudou ANTES de sobrescrever os valores atuais (historico)
		List<String> camposAlterados = new ArrayList<>();
		if (!Objects.equals(tarefa.getTitulo(), dadosRecebidos.getTitulo())) {
			camposAlterados.add("título");
		}
		if (!Objects.equals(tarefa.getDescricao(), dadosRecebidos.getDescricao())) {
			camposAlterados.add("descrição");
		}
		if (!Objects.equals(tarefa.getPrazo(), dadosRecebidos.getPrazo())) {
			camposAlterados.add("prazo");
		}
		if (tarefa.getPrioridade() != dadosRecebidos.getPrioridade()) {
			camposAlterados.add("prioridade");
		}
		if (!mesmosResponsaveis(tarefa.getResponsaveis(), novosResponsaveis)) {
			camposAlterados.add("responsáveis");
		}
		if (!mesmasTarefas(tarefa.getDependencias(), novasDependencias)) {
			camposAlterados.add("dependências");
		}

		// prazo alterado: a rotina de atrasos volta a avaliar esta tarefa (RF-06.2)
		if (!Objects.equals(tarefa.getPrazo(), dadosRecebidos.getPrazo())) {
			tarefa.setNotificadoAtraso(false);
		}

		tarefa.setTitulo(dadosRecebidos.getTitulo());
		tarefa.setDescricao(dadosRecebidos.getDescricao());
		tarefa.setPrazo(dadosRecebidos.getPrazo());
		tarefa.setPrioridade(dadosRecebidos.getPrioridade());
		tarefa.setResponsaveis(novosResponsaveis);
		tarefa.setDependencias(novasDependencias);
		Tarefa tarefaSalva = tarefaRepositorio.save(tarefa);

		if (!responsaveisAdicionados.isEmpty()) {
			notificarAtribuicao(tarefaSalva, responsaveisAdicionados, usuarioLogado);
		}
		if (!camposAlterados.isEmpty()) {
			historicoTarefaServico.registrar(tarefaSalva, usuarioLogado,
					"Editou " + String.join(", ", camposAlterados) + ".");
		}
		return tarefaSalva;
	}

	/** Move a tarefa de coluna no Kanban (RF-03.4 — chamado pelo drag-and-drop do frontend). */
	public Tarefa moverStatus(Usuario usuarioLogado, Long tarefaId, String status) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		MembroProjeto vinculo = validarPodeAlterar(tarefa, usuarioLogado);
		validarProjetoAtivo(tarefa.getProjeto());

		StatusTarefa statusAnterior = tarefa.getStatus();
		StatusTarefa novoStatus = converterStatus(status);

		// so sai de "A Fazer" quando todas as dependencias estiverem concluidas
		if (novoStatus != StatusTarefa.A_FAZER) {
			List<String> pendentes = tarefa.getDependencias().stream()
					.filter(dependencia -> dependencia.getStatus() != StatusTarefa.CONCLUIDO)
					.map(Tarefa::getTitulo)
					.toList();
			if (!pendentes.isEmpty()) {
				throw new RegraDeNegocioExcecao("Esta tarefa depende de \"" + String.join("\", \"", pendentes)
						+ "\", que ainda nao " + (pendentes.size() == 1 ? "foi concluida" : "foram concluidas") + ".");
			}
		}

		// RF-03.7: concluir e desfazer a conclusao sao decisoes do Gestor —
		// o Membro entrega o trabalho movendo a tarefa para Em Revisao
		if (vinculo.getPapelNoProjeto() != PapelProjeto.GESTOR) {
			if (novoStatus == StatusTarefa.CONCLUIDO) {
				throw new PermissaoNegadaExcecao(
						"Somente o Gestor conclui tarefas. Mova para 'Em Revisao' e aguarde a avaliacao.");
			}
			if (statusAnterior == StatusTarefa.CONCLUIDO) {
				throw new PermissaoNegadaExcecao("Somente o Gestor pode reabrir uma tarefa concluida.");
			}
		}

		boolean acabouDeSerBloqueada = novoStatus == StatusTarefa.BLOQUEADO
				&& statusAnterior != StatusTarefa.BLOQUEADO;
		boolean acabouDeEntrarEmRevisao = novoStatus == StatusTarefa.EM_REVISAO
				&& statusAnterior != StatusTarefa.EM_REVISAO;

		// registra quando a tarefa foi concluida (usado pelo relatorio, RF-04.2);
		// se ela voltar para outra coluna, o registro e apagado
		if (novoStatus == StatusTarefa.CONCLUIDO && statusAnterior != StatusTarefa.CONCLUIDO) {
			tarefa.setConcluidaEm(LocalDateTime.now());
		} else if (novoStatus != StatusTarefa.CONCLUIDO) {
			tarefa.setConcluidaEm(null);
		}

		tarefa.setStatus(novoStatus);
		Tarefa tarefaSalva = tarefaRepositorio.save(tarefa);
		if (statusAnterior != novoStatus) {
			historicoTarefaServico.registrar(tarefaSalva, usuarioLogado,
					"Moveu de \"" + rotuloDoStatus(statusAnterior) + "\" para \"" + rotuloDoStatus(novoStatus) + "\".");
		}
		if (acabouDeSerBloqueada) {
			notificarBloqueio(tarefaSalva, usuarioLogado);
		}
		if (acabouDeEntrarEmRevisao) {
			notificarRevisao(tarefaSalva, usuarioLogado);
		}
		return tarefaSalva;
	}

	/** Exclui uma tarefa — somente o Gestor (RF-03.2). */
	public void excluir(Usuario usuarioLogado, Long tarefaId) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		MembroProjeto vinculo = projetoServico.buscarVinculoObrigatorio(
				tarefa.getProjeto().getId(), usuarioLogado);
		if (vinculo.getPapelNoProjeto() != PapelProjeto.GESTOR) {
			throw new PermissaoNegadaExcecao("Somente o Gestor do projeto pode excluir tarefas.");
		}
		validarProjetoAtivo(tarefa.getProjeto());
		// remove o historico e as referencias de quem depende desta tarefa antes,
		// senao a chave estrangeira impede a exclusao
		historicoTarefaServico.excluirDaTarefa(tarefaId);
		List<Tarefa> dependentes = tarefaRepositorio.buscarQueDependemDe(tarefaId);
		for (Tarefa dependente : dependentes) {
			dependente.getDependencias().removeIf(dependencia -> dependencia.getId().equals(tarefaId));
		}
		tarefaRepositorio.saveAll(dependentes);
		tarefaRepositorio.delete(tarefa);
	}

	// ---------- notificacoes disparadas pelas acoes do quadro (RF-06) ----------

	/** RF-06.3: avisa os responsaveis recem-atribuidos a tarefa por outra pessoa. */
	private void notificarAtribuicao(Tarefa tarefa, List<Usuario> responsaveisAtribuidos, Usuario quemAtribuiu) {
		for (Usuario responsavel : responsaveisAtribuidos) {
			if (!responsavel.getId().equals(quemAtribuiu.getId())) {
				notificacaoServico.criar(responsavel, TipoNotificacao.TAREFA_ATRIBUIDA,
						"Voce foi atribuido a tarefa '" + tarefa.getTitulo() + "' do projeto '"
								+ tarefa.getProjeto().getNome() + "'.");
			}
		}
	}

	/** RF-06.1: avisa o Gestor quando uma tarefa e marcada como bloqueada por outra pessoa. */
	private void notificarBloqueio(Tarefa tarefa, Usuario quemBloqueou) {
		Usuario gestor = tarefa.getProjeto().getCriadoPor();
		if (!gestor.getId().equals(quemBloqueou.getId())) {
			notificacaoServico.criar(gestor, TipoNotificacao.TAREFA_BLOQUEADA,
					"A tarefa '" + tarefa.getTitulo() + "' do projeto '"
							+ tarefa.getProjeto().getNome() + "' foi marcada como bloqueada.");
		}
	}

	/** RF-06.6: avisa o Gestor quando uma tarefa entra em revisao aguardando a avaliacao dele. */
	private void notificarRevisao(Tarefa tarefa, Usuario quemEntregou) {
		Usuario gestor = tarefa.getProjeto().getCriadoPor();
		if (!gestor.getId().equals(quemEntregou.getId())) {
			notificacaoServico.criar(gestor, TipoNotificacao.TAREFA_EM_REVISAO,
					"A tarefa '" + tarefa.getTitulo() + "' do projeto '"
							+ tarefa.getProjeto().getNome() + "' foi entregue e aguarda a sua avaliacao.");
		}
	}

	// ---------- validacoes internas ----------

	private Tarefa buscarTarefa(Long tarefaId) {
		return tarefaRepositorio.findById(tarefaId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Tarefa nao encontrada."));
	}

	/**
	 * Gestor altera qualquer tarefa; Membro so as atribuidas a ele; Observador nenhuma.
	 * Publica porque o AnexoServico usa a mesma regra para os anexos (RF-07.2).
	 */
	public MembroProjeto validarPodeAlterar(Tarefa tarefa, Usuario usuario) {
		MembroProjeto vinculo = projetoServico.buscarVinculoObrigatorio(
				tarefa.getProjeto().getId(), usuario);
		if (vinculo.getPapelNoProjeto() == PapelProjeto.OBSERVADOR) {
			throw new PermissaoNegadaExcecao("Observador tem acesso somente leitura ao projeto.");
		}
		boolean souUmDosResponsaveis = tarefa.getResponsaveis().stream()
				.anyMatch(responsavel -> responsavel.getId().equals(usuario.getId()));
		if (vinculo.getPapelNoProjeto() == PapelProjeto.MEMBRO && !souUmDosResponsaveis) {
			throw new PermissaoNegadaExcecao("Um Membro so pode alterar tarefas atribuidas a ele.");
		}
		return vinculo;
	}

	/**
	 * Valida e resolve a lista de responsaveis recebida (nula/vazia = tarefa sem
	 * responsavel). Ids repetidos sao ignorados; a ordem de chegada e preservada.
	 */
	private List<Usuario> validarResponsaveis(Long projetoId, List<Usuario> recebidos) {
		if (recebidos == null || recebidos.isEmpty()) {
			return new ArrayList<>();
		}
		Set<Long> idsUnicos = new LinkedHashSet<>();
		for (Usuario usuario : recebidos) {
			if (usuario != null && usuario.getId() != null) {
				idsUnicos.add(usuario.getId());
			}
		}
		List<Usuario> responsaveis = new ArrayList<>();
		for (Long id : idsUnicos) {
			responsaveis.add(validarResponsavel(projetoId, id));
		}
		return responsaveis;
	}

	/** O responsavel precisa ser membro ativo do projeto e nao pode ser Observador. */
	private Usuario validarResponsavel(Long projetoId, Long responsavelId) {
		MembroProjeto vinculoDoResponsavel = membroProjetoRepositorio
				.buscarVinculoAtivo(projetoId, responsavelId)
				.orElseThrow(() -> new RegraDeNegocioExcecao(
						"O responsavel precisa ser membro ativo do projeto."));
		if (vinculoDoResponsavel.getPapelNoProjeto() == PapelProjeto.OBSERVADOR) {
			throw new RegraDeNegocioExcecao("Um Observador nao pode ser responsavel por tarefas.");
		}
		return vinculoDoResponsavel.getUsuario();
	}

	/** Compara duas listas de responsaveis pelo conjunto de ids, ignorando a ordem. */
	private boolean mesmosResponsaveis(List<Usuario> atuais, List<Usuario> novos) {
		Set<Long> idsAtuais = atuais.stream().map(Usuario::getId).collect(Collectors.toSet());
		Set<Long> idsNovos = novos.stream().map(Usuario::getId).collect(Collectors.toSet());
		return idsAtuais.equals(idsNovos);
	}

	/**
	 * Valida e resolve a lista de dependencias recebida (nula/vazia = sem
	 * dependencia). Cada dependencia precisa ser uma tarefa do mesmo projeto,
	 * diferente da propria tarefa. Ids repetidos sao ignorados.
	 */
	private List<Tarefa> validarDependencias(Long projetoId, Long tarefaAtualId, List<Tarefa> recebidas) {
		if (recebidas == null || recebidas.isEmpty()) {
			return new ArrayList<>();
		}
		Set<Long> idsUnicos = new LinkedHashSet<>();
		for (Tarefa tarefa : recebidas) {
			if (tarefa != null && tarefa.getId() != null) {
				idsUnicos.add(tarefa.getId());
			}
		}
		List<Tarefa> dependencias = new ArrayList<>();
		for (Long id : idsUnicos) {
			if (id.equals(tarefaAtualId)) {
				throw new RegraDeNegocioExcecao("Uma tarefa nao pode depender dela mesma.");
			}
			Tarefa dependencia = tarefaRepositorio.findById(id)
					.orElseThrow(() -> new RegraDeNegocioExcecao("Tarefa de dependencia nao encontrada."));
			if (!dependencia.getProjeto().getId().equals(projetoId)) {
				throw new RegraDeNegocioExcecao("A dependencia precisa ser uma tarefa do mesmo projeto.");
			}
			dependencias.add(dependencia);
		}
		return dependencias;
	}

	/** Impede que a nova lista de dependencias feche um ciclo (A depende de B que depende de A). */
	private void validarSemCiclo(Tarefa tarefa, List<Tarefa> novasDependencias) {
		for (Tarefa dependencia : novasDependencias) {
			if (alcancavel(dependencia, tarefa.getId(), new HashSet<>())) {
				throw new RegraDeNegocioExcecao("Essa dependencia criaria um ciclo: \"" + dependencia.getTitulo()
						+ "\" ja depende, direta ou indiretamente, desta tarefa.");
			}
		}
	}

	/** Percorre a cadeia de dependencias a partir de "origem" procurando "alvoId". */
	private boolean alcancavel(Tarefa origem, Long alvoId, Set<Long> visitados) {
		if (origem.getId().equals(alvoId)) {
			return true;
		}
		if (!visitados.add(origem.getId())) {
			return false;
		}
		for (Tarefa dependencia : origem.getDependencias()) {
			if (alcancavel(dependencia, alvoId, visitados)) {
				return true;
			}
		}
		return false;
	}

	/** Compara duas listas de tarefas pelo conjunto de ids, ignorando a ordem — usado para as dependencias. */
	private boolean mesmasTarefas(List<Tarefa> atuais, List<Tarefa> novas) {
		Set<Long> idsAtuais = atuais.stream().map(Tarefa::getId).collect(Collectors.toSet());
		Set<Long> idsNovas = novas.stream().map(Tarefa::getId).collect(Collectors.toSet());
		return idsAtuais.equals(idsNovas);
	}

	private void validarProjetoAtivo(Projeto projeto) {
		if (projeto.getStatus() == StatusProjeto.ENCERRADO) {
			throw new RegraDeNegocioExcecao("O projeto esta encerrado: as tarefas nao podem mais ser alteradas.");
		}
	}

	private StatusTarefa converterStatus(String status) {
		try {
			return StatusTarefa.valueOf(status == null ? "" : status.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new RegraDeNegocioExcecao(
					"Status invalido. Use A_FAZER, EM_ANDAMENTO, BLOQUEADO, EM_REVISAO ou CONCLUIDO.");
		}
	}

	private String rotuloDoStatus(StatusTarefa status) {
		return switch (status) {
			case A_FAZER -> "A Fazer";
			case EM_ANDAMENTO -> "Em Andamento";
			case BLOQUEADO -> "Bloqueado";
			case EM_REVISAO -> "Em Revisão";
			case CONCLUIDO -> "Concluído";
		};
	}

}
