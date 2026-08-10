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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.fatec.kanvox.modelo.Comentario;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.ComentarioServico;

/** Endpoints de comentarios nas tarefas (RF-07.1). */
@RestController
@RequestMapping("/api")
public class ComentarioControlador {

	private final ComentarioServico comentarioServico;

	public ComentarioControlador(ComentarioServico comentarioServico) {
		this.comentarioServico = comentarioServico;
	}

	/** Lista os comentarios da tarefa, do mais antigo para o mais novo. */
	@GetMapping("/tarefas/{tarefaId}/comentarios")
	public List<Comentario> listar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long tarefaId) {
		return comentarioServico.listar(usuarioLogado, tarefaId);
	}

	/** Escreve um comentario. Corpo esperado: { "texto": "..." } */
	@PostMapping("/tarefas/{tarefaId}/comentarios")
	public ResponseEntity<Comentario> criar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long tarefaId, @RequestBody Map<String, String> corpo) {
		Comentario criado = comentarioServico.criar(usuarioLogado, tarefaId, corpo.get("texto"));
		return ResponseEntity.status(HttpStatus.CREATED).body(criado);
	}

	/** Exclui um comentario (somente o autor ou o Gestor). */
	@DeleteMapping("/comentarios/{comentarioId}")
	public Map<String, String> excluir(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long comentarioId) {
		comentarioServico.excluir(usuarioLogado, comentarioId);
		return Map.of("mensagem", "Comentario excluido.");
	}

}
