package br.edu.fatec.kanvox.servico;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
 * - Gestor: cria, edita, move e exclui qualquer tarefa do projeto (RF-03.2)
 * - Membro: edita e move apenas as tarefas atribuidas a ele (RF-03.3); nao cria
 * - Observador: somente leitura
 */
@Service
@Transactional
public class TarefaServico {

	private final TarefaRepositorio tarefaRepositorio;
	private final MembroProjetoRepositorio membroProjetoRepositorio;
	private final ProjetoServico projetoServico;
	private final NotificacaoServico notificacaoServico;

	public TarefaServico(TarefaRepositorio tarefaRepositorio,
			MembroProjetoRepositorio membroProjetoRepositorio,
			ProjetoServico projetoServico,
			NotificacaoServico notificacaoServico) {
		this.tarefaRepositorio = tarefaRepositorio;
		this.membroProjetoRepositorio = membroProjetoRepositorio;
		this.projetoServico = projetoServico;
		this.notificacaoServico = notificacaoServico;
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
	 * atribui-la a qualquer membro ativo ou deixa-la sem responsavel.
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
		novaTarefa.setResponsavel(dadosRecebidos.getResponsavel() == null ? null
				: validarResponsavel(projetoId, dadosRecebidos.getResponsavel().getId()));
		Tarefa tarefaSalva = tarefaRepositorio.save(novaTarefa);
		notificarAtribuicao(tarefaSalva, usuarioLogado);
		return tarefaSalva;
	}

	/**
	 * Edita titulo, descricao, prazo e responsavel da tarefa.
	 * Gestor edita qualquer tarefa; Membro apenas as atribuidas a ele,
	 * e sem poder trocar o responsavel (reatribuir e acao de Gestor).
	 */
	public Tarefa editar(Usuario usuarioLogado, Long tarefaId, Tarefa dadosRecebidos) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		MembroProjeto vinculo = validarPodeAlterar(tarefa, usuarioLogado);
		validarProjetoAtivo(tarefa.getProjeto());
		if (dadosRecebidos.getTitulo() == null || dadosRecebidos.getTitulo().isBlank()) {
			throw new RegraDeNegocioExcecao("O titulo da tarefa e obrigatorio.");
		}

		Long novoResponsavelId = dadosRecebidos.getResponsavel() == null ? null
				: dadosRecebidos.getResponsavel().getId();
		Long responsavelAtualId = tarefa.getResponsavel() == null ? null
				: tarefa.getResponsavel().getId();
		boolean responsavelMudou = novoResponsavelId != null && !novoResponsavelId.equals(responsavelAtualId);

		if (vinculo.getPapelNoProjeto() == PapelProjeto.GESTOR) {
			// Gestor pode reatribuir (ou deixar sem responsavel, enviando null)
			tarefa.setResponsavel(novoResponsavelId == null ? null
					: validarResponsavel(tarefa.getProjeto().getId(), novoResponsavelId));
		} else if (responsavelMudou) {
			throw new PermissaoNegadaExcecao("Somente o Gestor pode alterar o responsavel da tarefa.");
		}

		// prazo alterado: a rotina de atrasos volta a avaliar esta tarefa (RF-06.2)
		if (!Objects.equals(tarefa.getPrazo(), dadosRecebidos.getPrazo())) {
			tarefa.setNotificadoAtraso(false);
		}

		tarefa.setTitulo(dadosRecebidos.getTitulo());
		tarefa.setDescricao(dadosRecebidos.getDescricao());
		tarefa.setPrazo(dadosRecebidos.getPrazo());
		Tarefa tarefaSalva = tarefaRepositorio.save(tarefa);
		if (responsavelMudou) {
			notificarAtribuicao(tarefaSalva, usuarioLogado);
		}
		return tarefaSalva;
	}

	/** Move a tarefa de coluna no Kanban (RF-03.4 — chamado pelo drag-and-drop do frontend). */
	public Tarefa moverStatus(Usuario usuarioLogado, Long tarefaId, String status) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		validarPodeAlterar(tarefa, usuarioLogado);
		validarProjetoAtivo(tarefa.getProjeto());

		StatusTarefa novoStatus = converterStatus(status);
		boolean acabouDeSerBloqueada = novoStatus == StatusTarefa.BLOQUEADO
				&& tarefa.getStatus() != StatusTarefa.BLOQUEADO;

		// registra quando a tarefa foi concluida (usado pelo relatorio, RF-04.2);
		// se ela voltar para outra coluna, o registro e apagado
		if (novoStatus == StatusTarefa.CONCLUIDO && tarefa.getStatus() != StatusTarefa.CONCLUIDO) {
			tarefa.setConcluidaEm(LocalDateTime.now());
		} else if (novoStatus != StatusTarefa.CONCLUIDO) {
			tarefa.setConcluidaEm(null);
		}

		tarefa.setStatus(novoStatus);
		Tarefa tarefaSalva = tarefaRepositorio.save(tarefa);
		if (acabouDeSerBloqueada) {
			notificarBloqueio(tarefaSalva, usuarioLogado);
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
		tarefaRepositorio.delete(tarefa);
	}

	// ---------- notificacoes disparadas pelas acoes do quadro (RF-06) ----------

	/** RF-06.3: avisa o responsavel quando uma tarefa e atribuida a ele por outra pessoa. */
	private void notificarAtribuicao(Tarefa tarefa, Usuario quemAtribuiu) {
		Usuario responsavel = tarefa.getResponsavel();
		if (responsavel != null && !responsavel.getId().equals(quemAtribuiu.getId())) {
			notificacaoServico.criar(responsavel, TipoNotificacao.TAREFA_ATRIBUIDA,
					"Voce foi atribuido a tarefa '" + tarefa.getTitulo() + "' do projeto '"
							+ tarefa.getProjeto().getNome() + "'.");
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

	// ---------- validacoes internas ----------

	private Tarefa buscarTarefa(Long tarefaId) {
		return tarefaRepositorio.findById(tarefaId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Tarefa nao encontrada."));
	}

	/** Gestor altera qualquer tarefa; Membro so as atribuidas a ele; Observador nenhuma. */
	private MembroProjeto validarPodeAlterar(Tarefa tarefa, Usuario usuario) {
		MembroProjeto vinculo = projetoServico.buscarVinculoObrigatorio(
				tarefa.getProjeto().getId(), usuario);
		if (vinculo.getPapelNoProjeto() == PapelProjeto.OBSERVADOR) {
			throw new PermissaoNegadaExcecao("Observador tem acesso somente leitura ao projeto.");
		}
		boolean eOResponsavel = tarefa.getResponsavel() != null
				&& tarefa.getResponsavel().getId().equals(usuario.getId());
		if (vinculo.getPapelNoProjeto() == PapelProjeto.MEMBRO && !eOResponsavel) {
			throw new PermissaoNegadaExcecao("Um Membro so pode alterar tarefas atribuidas a ele.");
		}
		return vinculo;
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

	private void validarProjetoAtivo(Projeto projeto) {
		if (projeto.getStatus() == StatusProjeto.ENCERRADO) {
			throw new RegraDeNegocioExcecao("O projeto esta encerrado: as tarefas nao podem mais ser alteradas.");
		}
	}

	private StatusTarefa converterStatus(String status) {
		try {
			return StatusTarefa.valueOf(status == null ? "" : status.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new RegraDeNegocioExcecao("Status invalido. Use A_FAZER, EM_ANDAMENTO, BLOQUEADO ou CONCLUIDO.");
		}
	}

}
