package br.edu.fatec.kanvox.servico;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.fatec.kanvox.modelo.MembroProjeto;
import br.edu.fatec.kanvox.modelo.PapelProjeto;
import br.edu.fatec.kanvox.modelo.Projeto;
import br.edu.fatec.kanvox.modelo.SituacaoNoProjeto;
import br.edu.fatec.kanvox.modelo.StatusProjeto;
import br.edu.fatec.kanvox.modelo.StatusTarefa;
import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.modelo.TipoNotificacao;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.MembroProjetoRepositorio;
import br.edu.fatec.kanvox.repositorio.ProjetoRepositorio;
import br.edu.fatec.kanvox.repositorio.TarefaRepositorio;
import br.edu.fatec.kanvox.repositorio.UsuarioRepositorio;

/**
 * Regras de negocio de projetos e membros (RF-02 e parte do RF-01).
 * Toda operacao de escrita valida o papel do usuario no projeto (RBAC)
 * aqui na camada de servico, antes de tocar no banco (RNF-02).
 */
@Service
@Transactional
public class ProjetoServico {

	private final ProjetoRepositorio projetoRepositorio;
	private final MembroProjetoRepositorio membroProjetoRepositorio;
	private final UsuarioRepositorio usuarioRepositorio;
	private final TarefaRepositorio tarefaRepositorio;
	private final NotificacaoServico notificacaoServico;

	public ProjetoServico(ProjetoRepositorio projetoRepositorio,
			MembroProjetoRepositorio membroProjetoRepositorio,
			UsuarioRepositorio usuarioRepositorio,
			TarefaRepositorio tarefaRepositorio,
			NotificacaoServico notificacaoServico) {
		this.projetoRepositorio = projetoRepositorio;
		this.membroProjetoRepositorio = membroProjetoRepositorio;
		this.usuarioRepositorio = usuarioRepositorio;
		this.tarefaRepositorio = tarefaRepositorio;
		this.notificacaoServico = notificacaoServico;
	}

	/** Cria um projeto; quem cria torna-se automaticamente o Gestor dele (RF-02.1). */
	public Projeto criar(Usuario usuarioLogado, Projeto dadosRecebidos) {
		if (dadosRecebidos.getNome() == null || dadosRecebidos.getNome().isBlank()) {
			throw new RegraDeNegocioExcecao("O nome do projeto e obrigatorio.");
		}

		// objeto novo: nunca confiamos em id/status vindos do corpo da requisicao
		Projeto novoProjeto = new Projeto();
		novoProjeto.setNome(dadosRecebidos.getNome());
		novoProjeto.setDescricao(dadosRecebidos.getDescricao());
		novoProjeto.setCriadoPor(usuarioLogado);
		projetoRepositorio.save(novoProjeto);

		MembroProjeto vinculoDoGestor = new MembroProjeto();
		vinculoDoGestor.setProjeto(novoProjeto);
		vinculoDoGestor.setUsuario(usuarioLogado);
		vinculoDoGestor.setPapelNoProjeto(PapelProjeto.GESTOR);
		// quem cria o projeto entra direto, sem passar por convite
		vinculoDoGestor.setSituacao(SituacaoNoProjeto.ATIVO);
		membroProjetoRepositorio.save(vinculoDoGestor);

		return novoProjeto;
	}

	/** Lista os projetos em que o usuario logado e membro ativo. */
	public List<Projeto> listarDoUsuario(Usuario usuarioLogado) {
		return projetoRepositorio.buscarPorMembro(usuarioLogado.getId());
	}

	/** Visao geral do projeto: dados, membros e progresso das tarefas (RF-02.3). */
	public Map<String, Object> buscarVisaoGeral(Usuario usuarioLogado, Long projetoId) {
		MembroProjeto vinculo = buscarVinculoObrigatorio(projetoId, usuarioLogado);
		Projeto projeto = vinculo.getProjeto();

		List<Tarefa> tarefas = tarefaRepositorio.buscarPorProjeto(projetoId);
		long totalDeTarefas = tarefas.size();
		long tarefasConcluidas = tarefas.stream()
				.filter(tarefa -> tarefa.getStatus() == StatusTarefa.CONCLUIDO)
				.count();
		int progresso = totalDeTarefas == 0 ? 0 : (int) (tarefasConcluidas * 100 / totalDeTarefas);
		long tarefasBloqueadas = tarefas.stream()
				.filter(tarefa -> tarefa.getStatus() == StatusTarefa.BLOQUEADO)
				.count();
		long tarefasAtrasadas = tarefas.stream()
				.filter(tarefa -> tarefa.getStatus() != StatusTarefa.CONCLUIDO
						&& tarefa.getPrazo() != null && tarefa.getPrazo().isBefore(LocalDate.now()))
				.count();

		Map<String, Object> visaoGeral = new HashMap<>();
		visaoGeral.put("projeto", projeto);
		// inclui convites pendentes (RF-01.4) — quem monta o select de responsavel
		// no frontend filtra apenas os membros com situacao ATIVO
		visaoGeral.put("membros", membroProjetoRepositorio.buscarMembrosEConvites(projetoId));
		visaoGeral.put("totalTarefas", totalDeTarefas);
		visaoGeral.put("tarefasConcluidas", tarefasConcluidas);
		visaoGeral.put("tarefasEmAberto", totalDeTarefas - tarefasConcluidas);
		visaoGeral.put("progresso", progresso);
		visaoGeral.put("tarefasBloqueadas", tarefasBloqueadas);
		visaoGeral.put("tarefasAtrasadas", tarefasAtrasadas);
		return visaoGeral;
	}

	/** Edita nome e descricao do projeto — somente o Gestor (RF-02.2). */
	public Projeto editar(Usuario usuarioLogado, Long projetoId, Projeto dadosRecebidos) {
		Projeto projeto = validarGestor(projetoId, usuarioLogado).getProjeto();
		if (projeto.getStatus() == StatusProjeto.ENCERRADO) {
			throw new RegraDeNegocioExcecao("Um projeto encerrado nao pode ser editado.");
		}
		if (dadosRecebidos.getNome() == null || dadosRecebidos.getNome().isBlank()) {
			throw new RegraDeNegocioExcecao("O nome do projeto e obrigatorio.");
		}
		projeto.setNome(dadosRecebidos.getNome());
		projeto.setDescricao(dadosRecebidos.getDescricao());
		return projetoRepositorio.save(projeto);
	}

	/** Encerra o projeto — somente o Gestor (RF-02.2). */
	public Projeto encerrar(Usuario usuarioLogado, Long projetoId) {
		Projeto projeto = validarGestor(projetoId, usuarioLogado).getProjeto();
		if (projeto.getStatus() == StatusProjeto.ENCERRADO) {
			throw new RegraDeNegocioExcecao("Este projeto ja esta encerrado.");
		}
		projeto.setStatus(StatusProjeto.ENCERRADO);
		return projetoRepositorio.save(projeto);
	}

	/**
	 * Convida um usuario ja cadastrado para o projeto — somente o Gestor (RF-01.4).
	 * O convite nasce PENDENTE: o convidado recebe uma notificacao e decide
	 * aceitar ou recusar. O papel pode ser MEMBRO ou OBSERVADOR.
	 * Se a pessoa ja participou e saiu, um novo convite reaproveita o vinculo.
	 */
	public MembroProjeto convidarMembro(Usuario usuarioLogado, Long projetoId, String email, String papel) {
		Projeto projeto = validarGestor(projetoId, usuarioLogado).getProjeto();
		if (projeto.getStatus() == StatusProjeto.ENCERRADO) {
			throw new RegraDeNegocioExcecao("Nao e possivel convidar membros para um projeto encerrado.");
		}

		Usuario convidado = usuarioRepositorio.buscarPorEmail(email == null ? "" : email)
				.orElseThrow(() -> new RegraDeNegocioExcecao(
						"Nenhum usuario cadastrado com este e-mail. O convite so funciona para quem ja tem conta."));

		PapelProjeto papelDoConvidado = converterPapel(papel);
		if (papelDoConvidado == PapelProjeto.GESTOR) {
			throw new RegraDeNegocioExcecao("O papel de Gestor pertence a quem criou o projeto. Convide como MEMBRO ou OBSERVADOR.");
		}

		MembroProjeto vinculo = membroProjetoRepositorio
				.buscarVinculo(projetoId, convidado.getId()).orElse(null);
		if (vinculo != null && vinculo.getSituacao() == SituacaoNoProjeto.ATIVO) {
			throw new RegraDeNegocioExcecao("Este usuario ja e membro do projeto.");
		}
		if (vinculo != null && vinculo.getSituacao() == SituacaoNoProjeto.PENDENTE) {
			throw new RegraDeNegocioExcecao("Ja existe um convite pendente para este usuario.");
		}

		if (vinculo == null) {
			vinculo = new MembroProjeto();
			vinculo.setProjeto(projeto);
			vinculo.setUsuario(convidado);
		}
		// vinculo novo ou reaproveitado de quem saiu: vira um convite pendente
		vinculo.setPapelNoProjeto(papelDoConvidado);
		vinculo.setSituacao(SituacaoNoProjeto.PENDENTE);
		MembroProjeto convite = membroProjetoRepositorio.save(vinculo);

		notificacaoServico.criar(convidado, TipoNotificacao.CONVITE_PROJETO,
				usuarioLogado.getNome() + " convidou voce para o projeto '" + projeto.getNome()
						+ "'. Responda na tela Meus projetos.");
		return convite;
	}

	/** Convites pendentes recebidos pelo usuario logado (RF-01.4). */
	public List<Map<String, Object>> listarConvites(Usuario usuarioLogado) {
		return membroProjetoRepositorio.buscarConvitesPendentes(usuarioLogado.getId()).stream()
				.map(convite -> {
					Map<String, Object> dados = new HashMap<>();
					dados.put("id", convite.getId());
					dados.put("papelNoProjeto", convite.getPapelNoProjeto());
					dados.put("projeto", Map.of(
							"id", convite.getProjeto().getId(),
							"nome", convite.getProjeto().getNome()));
					dados.put("convidadoPor", convite.getProjeto().getCriadoPor().getNome());
					return dados;
				})
				.toList();
	}

	/** O convidado aceita o convite e passa a participar do projeto (RF-01.4). */
	public MembroProjeto aceitarConvite(Usuario usuarioLogado, Long conviteId) {
		MembroProjeto convite = buscarConvitePendenteDoUsuario(usuarioLogado, conviteId);
		convite.setSituacao(SituacaoNoProjeto.ATIVO);
		MembroProjeto aceito = membroProjetoRepositorio.save(convite);
		notificacaoServico.criar(convite.getProjeto().getCriadoPor(), TipoNotificacao.CONVITE_RESPONDIDO,
				usuarioLogado.getNome() + " aceitou o convite para o projeto '"
						+ convite.getProjeto().getNome() + "'.");
		return aceito;
	}

	/** O convidado recusa o convite; o vinculo vira INATIVO (RF-01.4). */
	public void recusarConvite(Usuario usuarioLogado, Long conviteId) {
		MembroProjeto convite = buscarConvitePendenteDoUsuario(usuarioLogado, conviteId);
		convite.setSituacao(SituacaoNoProjeto.INATIVO);
		membroProjetoRepositorio.save(convite);
		notificacaoServico.criar(convite.getProjeto().getCriadoPor(), TipoNotificacao.CONVITE_RESPONDIDO,
				usuarioLogado.getNome() + " recusou o convite para o projeto '"
						+ convite.getProjeto().getNome() + "'.");
	}

	private MembroProjeto buscarConvitePendenteDoUsuario(Usuario usuario, Long conviteId) {
		MembroProjeto convite = membroProjetoRepositorio.findById(conviteId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Convite nao encontrado."));
		if (!convite.getUsuario().getId().equals(usuario.getId())) {
			throw new PermissaoNegadaExcecao("Este convite nao pertence a voce.");
		}
		if (convite.getSituacao() != SituacaoNoProjeto.PENDENTE) {
			throw new RegraDeNegocioExcecao("Este convite ja foi respondido.");
		}
		return convite;
	}

	/**
	 * Remove um membro (ou cancela um convite pendente) — somente o Gestor.
	 * O vinculo vira INATIVO, nunca e excluido (RF-01.5).
	 */
	public void removerMembro(Usuario usuarioLogado, Long projetoId, Long usuarioId) {
		validarGestor(projetoId, usuarioLogado);

		MembroProjeto vinculoDoMembro = membroProjetoRepositorio.buscarVinculo(projetoId, usuarioId)
				.filter(vinculo -> vinculo.getSituacao() != SituacaoNoProjeto.INATIVO)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Este usuario nao e membro ativo do projeto."));
		if (vinculoDoMembro.getPapelNoProjeto() == PapelProjeto.GESTOR) {
			throw new RegraDeNegocioExcecao("O Gestor nao pode ser removido do proprio projeto.");
		}
		vinculoDoMembro.setSituacao(SituacaoNoProjeto.INATIVO);
		membroProjetoRepositorio.save(vinculoDoMembro);
	}

	/** Membro sai voluntariamente do projeto (RF-01.5). O Gestor nao pode sair. */
	public void sairDoProjeto(Usuario usuarioLogado, Long projetoId) {
		MembroProjeto vinculo = buscarVinculoObrigatorio(projetoId, usuarioLogado);
		if (vinculo.getPapelNoProjeto() == PapelProjeto.GESTOR) {
			throw new PermissaoNegadaExcecao("O Gestor nao pode sair do proprio projeto.");
		}
		vinculo.setSituacao(SituacaoNoProjeto.INATIVO);
		membroProjetoRepositorio.save(vinculo);
	}

	/** Lista os membros ativos e os convites pendentes do projeto — qualquer membro pode consultar. */
	public List<MembroProjeto> listarMembros(Usuario usuarioLogado, Long projetoId) {
		buscarVinculoObrigatorio(projetoId, usuarioLogado);
		return membroProjetoRepositorio.buscarMembrosEConvites(projetoId);
	}

	// ---------- validacoes de RBAC ----------
	// Publicas porque outros servicos (ex. TarefaServico) reutilizam
	// as mesmas verificacoes de acesso ao projeto.

	/** Garante que o usuario e membro ativo do projeto; caso contrario, 403. */
	public MembroProjeto buscarVinculoObrigatorio(Long projetoId, Usuario usuario) {
		if (!projetoRepositorio.existsById(projetoId)) {
			throw new RegraDeNegocioExcecao("Projeto nao encontrado.");
		}
		return membroProjetoRepositorio.buscarVinculoAtivo(projetoId, usuario.getId())
				.orElseThrow(() -> new PermissaoNegadaExcecao("Voce nao participa deste projeto."));
	}

	/** Garante que o usuario e o Gestor do projeto; caso contrario, 403. */
	public MembroProjeto validarGestor(Long projetoId, Usuario usuario) {
		MembroProjeto vinculo = buscarVinculoObrigatorio(projetoId, usuario);
		if (vinculo.getPapelNoProjeto() != PapelProjeto.GESTOR) {
			throw new PermissaoNegadaExcecao("Somente o Gestor do projeto pode executar esta operacao.");
		}
		return vinculo;
	}

	private PapelProjeto converterPapel(String papel) {
		try {
			return PapelProjeto.valueOf(papel == null ? "" : papel.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new RegraDeNegocioExcecao("Papel invalido. Use MEMBRO ou OBSERVADOR.");
		}
	}

}
