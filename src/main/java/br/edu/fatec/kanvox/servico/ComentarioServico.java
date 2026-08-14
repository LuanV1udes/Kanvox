package br.edu.fatec.kanvox.servico;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.fatec.kanvox.modelo.Comentario;
import br.edu.fatec.kanvox.modelo.MembroProjeto;
import br.edu.fatec.kanvox.modelo.PapelProjeto;
import br.edu.fatec.kanvox.modelo.StatusProjeto;
import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.modelo.TipoNotificacao;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.ComentarioRepositorio;
import br.edu.fatec.kanvox.repositorio.TarefaRepositorio;

/**
 * Regras de negocio dos comentarios nas tarefas (RF-07.1) — o canal de
 * devolutiva entre Membro e Gestor. Gestor e Membro comentam em qualquer
 * tarefa do projeto; Observador so le.
 */
@Service
@Transactional
public class ComentarioServico {

	private final ComentarioRepositorio comentarioRepositorio;
	private final TarefaRepositorio tarefaRepositorio;
	private final ProjetoServico projetoServico;
	private final NotificacaoServico notificacaoServico;

	public ComentarioServico(ComentarioRepositorio comentarioRepositorio,
			TarefaRepositorio tarefaRepositorio,
			ProjetoServico projetoServico,
			NotificacaoServico notificacaoServico) {
		this.comentarioRepositorio = comentarioRepositorio;
		this.tarefaRepositorio = tarefaRepositorio;
		this.projetoServico = projetoServico;
		this.notificacaoServico = notificacaoServico;
	}

	/** Lista os comentarios de uma tarefa — qualquer membro ativo do projeto. */
	public List<Comentario> listar(Usuario usuarioLogado, Long tarefaId) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		projetoServico.buscarVinculoObrigatorio(tarefa.getProjeto().getId(), usuarioLogado);
		return comentarioRepositorio.buscarPorTarefa(tarefaId);
	}

	/** Escreve um comentario e avisa os envolvidos (RF-06.5). */
	public Comentario criar(Usuario usuarioLogado, Long tarefaId, String texto) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		MembroProjeto vinculo = projetoServico.buscarVinculoObrigatorio(
				tarefa.getProjeto().getId(), usuarioLogado);
		if (vinculo.getPapelNoProjeto() == PapelProjeto.OBSERVADOR) {
			throw new PermissaoNegadaExcecao("Observador tem acesso somente leitura ao projeto.");
		}
		validarProjetoAtivo(tarefa);
		if (texto == null || texto.isBlank()) {
			throw new RegraDeNegocioExcecao("O comentario nao pode ser vazio.");
		}

		Comentario comentario = new Comentario();
		comentario.setTarefa(tarefa);
		comentario.setAutor(usuarioLogado);
		comentario.setTexto(texto.trim());
		Comentario salvo = comentarioRepositorio.save(comentario);

		notificarEnvolvidos(tarefa, usuarioLogado);
		return salvo;
	}

	/** Exclui um comentario — somente o proprio autor ou o Gestor do projeto. */
	public void excluir(Usuario usuarioLogado, Long comentarioId) {
		Comentario comentario = comentarioRepositorio.findById(comentarioId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Comentario nao encontrado."));
		MembroProjeto vinculo = projetoServico.buscarVinculoObrigatorio(
				comentario.getTarefa().getProjeto().getId(), usuarioLogado);

		boolean souOAutor = comentario.getAutor().getId().equals(usuarioLogado.getId());
		boolean souOGestor = vinculo.getPapelNoProjeto() == PapelProjeto.GESTOR;
		if (!souOAutor && !souOGestor) {
			throw new PermissaoNegadaExcecao("Somente o autor do comentario ou o Gestor podem exclui-lo.");
		}
		comentarioRepositorio.delete(comentario);
	}

	/** Avisa o responsavel pela tarefa e o Gestor sobre o novo comentario, exceto quem escreveu. */
	private void notificarEnvolvidos(Tarefa tarefa, Usuario autor) {
		String mensagem = autor.getNome() + " comentou na tarefa '" + tarefa.getTitulo()
				+ "' do projeto '" + tarefa.getProjeto().getNome() + "'.";

		Usuario gestor = tarefa.getProjeto().getCriadoPor();
		if (!gestor.getId().equals(autor.getId())) {
			notificacaoServico.criar(gestor, TipoNotificacao.NOVO_COMENTARIO, mensagem);
		}
		for (Usuario responsavel : tarefa.getResponsaveis()) {
			if (!responsavel.getId().equals(autor.getId()) && !responsavel.getId().equals(gestor.getId())) {
				notificacaoServico.criar(responsavel, TipoNotificacao.NOVO_COMENTARIO, mensagem);
			}
		}
	}

	private Tarefa buscarTarefa(Long tarefaId) {
		return tarefaRepositorio.findById(tarefaId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Tarefa nao encontrada."));
	}

	private void validarProjetoAtivo(Tarefa tarefa) {
		if (tarefa.getProjeto().getStatus() == StatusProjeto.ENCERRADO) {
			throw new RegraDeNegocioExcecao("O projeto esta encerrado: nao e possivel comentar.");
		}
	}

}
