package br.edu.fatec.kanvox.controlador;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.TarefaServico;

/**
 * Endpoints do quadro Kanban (RF-03).
 * Criacao e listagem ficam sob /api/projetos/{projetoId}/tarefas
 * (a tarefa sempre pertence a um projeto); as operacoes sobre uma
 * tarefa especifica ficam sob /api/tarefas/{tarefaId}.
 */
@RestController
@RequestMapping("/api")
public class TarefaControlador {

	private final TarefaServico tarefaServico;

	public TarefaControlador(TarefaServico tarefaServico) {
		this.tarefaServico = tarefaServico;
	}

	/** Lista as tarefas do projeto — endpoint consultado pelo polling do frontend (RF-03.6). */
	@GetMapping("/projetos/{projetoId}/tarefas")
	public List<Tarefa> listarPorProjeto(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId) {
		return tarefaServico.listarPorProjeto(usuarioLogado, projetoId);
	}

	/**
	 * Cria uma tarefa. Corpo esperado:
	 * { "titulo": "...", "descricao": "...", "prazo": "2026-08-01", "responsaveis": [{ "id": 2 }] }
	 * (prazo e responsaveis sao opcionais)
	 */
	@PostMapping("/projetos/{projetoId}/tarefas")
	public ResponseEntity<Tarefa> criar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId, @RequestBody Tarefa tarefa) {
		Tarefa criada = tarefaServico.criar(usuarioLogado, projetoId, tarefa);
		return ResponseEntity.status(HttpStatus.CREATED).body(criada);
	}

	/** Edita titulo, descricao, prazo e responsaveis (mesmo corpo da criacao). */
	@PutMapping("/tarefas/{tarefaId}")
	public Tarefa editar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long tarefaId, @RequestBody Tarefa tarefa) {
		return tarefaServico.editar(usuarioLogado, tarefaId, tarefa);
	}

	/** Move a tarefa de coluna (RF-03.4). Corpo esperado: { "status": "EM_ANDAMENTO" } */
	@PutMapping("/tarefas/{tarefaId}/status")
	public Tarefa moverStatus(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long tarefaId, @RequestBody Map<String, String> corpo) {
		return tarefaServico.moverStatus(usuarioLogado, tarefaId, corpo.get("status"));
	}

	/** Exclui a tarefa — somente o Gestor (RF-03.2). */
	@DeleteMapping("/tarefas/{tarefaId}")
	public Map<String, String> excluir(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long tarefaId) {
		tarefaServico.excluir(usuarioLogado, tarefaId);
		return Map.of("mensagem", "Tarefa excluida.");
	}

}
