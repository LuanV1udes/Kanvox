package br.edu.fatec.kanvox.servico;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.fatec.kanvox.modelo.HistoricoTarefa;
import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.HistoricoTarefaRepositorio;
import br.edu.fatec.kanvox.repositorio.TarefaRepositorio;

/**
 * Linha do tempo de atividade da tarefa: quem fez o que e quando.
 * Os registros sao gerados automaticamente pelo TarefaServico a cada
 * acao (criar, editar, mover de coluna) — nao existe escrita pelo usuario.
 */
@Service
@Transactional
public class HistoricoTarefaServico {

	private final HistoricoTarefaRepositorio historicoTarefaRepositorio;
	private final TarefaRepositorio tarefaRepositorio;
	private final ProjetoServico projetoServico;

	public HistoricoTarefaServico(HistoricoTarefaRepositorio historicoTarefaRepositorio,
			TarefaRepositorio tarefaRepositorio,
			ProjetoServico projetoServico) {
		this.historicoTarefaRepositorio = historicoTarefaRepositorio;
		this.tarefaRepositorio = tarefaRepositorio;
		this.projetoServico = projetoServico;
	}

	/** Registra um evento na linha do tempo — chamado pelo TarefaServico apos cada acao. */
	public void registrar(Tarefa tarefa, Usuario autor, String descricao) {
		HistoricoTarefa historico = new HistoricoTarefa();
		historico.setTarefa(tarefa);
		historico.setAutor(autor);
		historico.setDescricao(descricao);
		historicoTarefaRepositorio.save(historico);
	}

	/** Lista o historico da tarefa, do mais recente para o mais antigo — qualquer membro ativo do projeto. */
	public List<HistoricoTarefa> listar(Usuario usuarioLogado, Long tarefaId) {
		Tarefa tarefa = buscarTarefa(tarefaId);
		projetoServico.buscarVinculoObrigatorio(tarefa.getProjeto().getId(), usuarioLogado);
		return historicoTarefaRepositorio.buscarPorTarefa(tarefaId);
	}

	/**
	 * Remove o historico de uma tarefa — chamado pelo TarefaServico antes de
	 * exclui-la, para nao violar a chave estrangeira (historico_tarefa.tarefa_id).
	 */
	public void excluirDaTarefa(Long tarefaId) {
		historicoTarefaRepositorio.deleteAll(historicoTarefaRepositorio.buscarPorTarefa(tarefaId));
	}

	private Tarefa buscarTarefa(Long tarefaId) {
		return tarefaRepositorio.findById(tarefaId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Tarefa nao encontrada."));
	}

}
