package br.edu.fatec.kanvox.servico;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.fatec.kanvox.modelo.MembroProjeto;
import br.edu.fatec.kanvox.modelo.PapelProjeto;
import br.edu.fatec.kanvox.modelo.Projeto;
import br.edu.fatec.kanvox.modelo.StatusProjeto;
import br.edu.fatec.kanvox.modelo.StatusTarefa;
import br.edu.fatec.kanvox.modelo.Tarefa;
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

	public ProjetoServico(ProjetoRepositorio projetoRepositorio,
			MembroProjetoRepositorio membroProjetoRepositorio,
			UsuarioRepositorio usuarioRepositorio,
			TarefaRepositorio tarefaRepositorio) {
		this.projetoRepositorio = projetoRepositorio;
		this.membroProjetoRepositorio = membroProjetoRepositorio;
		this.usuarioRepositorio = usuarioRepositorio;
		this.tarefaRepositorio = tarefaRepositorio;
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

		Map<String, Object> visaoGeral = new HashMap<>();
		visaoGeral.put("projeto", projeto);
		visaoGeral.put("membros", membroProjetoRepositorio.buscarMembrosAtivos(projetoId));
		visaoGeral.put("totalTarefas", totalDeTarefas);
		visaoGeral.put("tarefasConcluidas", tarefasConcluidas);
		visaoGeral.put("tarefasEmAberto", totalDeTarefas - tarefasConcluidas);
		visaoGeral.put("progresso", progresso);
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
	 * O papel do convidado pode ser MEMBRO ou OBSERVADOR (o Gestor e unico: quem criou).
	 * Se a pessoa ja participou e saiu, o vinculo antigo e reativado (RF-01.5).
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

		MembroProjeto vinculoExistente = membroProjetoRepositorio
				.buscarVinculo(projetoId, convidado.getId()).orElse(null);
		if (vinculoExistente != null && vinculoExistente.isAtivo()) {
			throw new RegraDeNegocioExcecao("Este usuario ja e membro do projeto.");
		}
		if (vinculoExistente != null) {
			// ja participou e saiu: reativa o vinculo, preservando o historico
			vinculoExistente.setAtivo(true);
			vinculoExistente.setPapelNoProjeto(papelDoConvidado);
			return membroProjetoRepositorio.save(vinculoExistente);
		}

		MembroProjeto novoVinculo = new MembroProjeto();
		novoVinculo.setProjeto(projeto);
		novoVinculo.setUsuario(convidado);
		novoVinculo.setPapelNoProjeto(papelDoConvidado);
		return membroProjetoRepositorio.save(novoVinculo);
	}

	/** Remove um membro do projeto — somente o Gestor; o vinculo e desativado, nunca excluido (RF-01.5). */
	public void removerMembro(Usuario usuarioLogado, Long projetoId, Long usuarioId) {
		validarGestor(projetoId, usuarioLogado);

		MembroProjeto vinculoDoMembro = membroProjetoRepositorio.buscarVinculoAtivo(projetoId, usuarioId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Este usuario nao e membro ativo do projeto."));
		if (vinculoDoMembro.getPapelNoProjeto() == PapelProjeto.GESTOR) {
			throw new RegraDeNegocioExcecao("O Gestor nao pode ser removido do proprio projeto.");
		}
		vinculoDoMembro.setAtivo(false);
		membroProjetoRepositorio.save(vinculoDoMembro);
	}

	/** Membro sai voluntariamente do projeto (RF-01.5). O Gestor nao pode sair. */
	public void sairDoProjeto(Usuario usuarioLogado, Long projetoId) {
		MembroProjeto vinculo = buscarVinculoObrigatorio(projetoId, usuarioLogado);
		if (vinculo.getPapelNoProjeto() == PapelProjeto.GESTOR) {
			throw new PermissaoNegadaExcecao("O Gestor nao pode sair do proprio projeto.");
		}
		vinculo.setAtivo(false);
		membroProjetoRepositorio.save(vinculo);
	}

	/** Lista os membros ativos do projeto — qualquer membro pode consultar. */
	public List<MembroProjeto> listarMembros(Usuario usuarioLogado, Long projetoId) {
		buscarVinculoObrigatorio(projetoId, usuarioLogado);
		return membroProjetoRepositorio.buscarMembrosAtivos(projetoId);
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
