package br.edu.fatec.kanvox.controlador;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.fatec.kanvox.modelo.HistoricoTarefa;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.HistoricoTarefaServico;

/** Endpoint de leitura da linha do tempo de uma tarefa (criacao, edicoes, mudancas de coluna). */
@RestController
@RequestMapping("/api")
public class HistoricoTarefaControlador {

	private final HistoricoTarefaServico historicoTarefaServico;

	public HistoricoTarefaControlador(HistoricoTarefaServico historicoTarefaServico) {
		this.historicoTarefaServico = historicoTarefaServico;
	}

	/** Lista o historico da tarefa, do mais recente para o mais antigo — qualquer membro ativo do projeto. */
	@GetMapping("/tarefas/{tarefaId}/historico")
	public List<HistoricoTarefa> listar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long tarefaId) {
		return historicoTarefaServico.listar(usuarioLogado, tarefaId);
	}

}
