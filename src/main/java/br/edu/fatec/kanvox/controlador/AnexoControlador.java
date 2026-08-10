package br.edu.fatec.kanvox.controlador;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.edu.fatec.kanvox.modelo.Anexo;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.AnexoServico;

/** Endpoints de anexos nas tarefas (RF-07.2). */
@RestController
@RequestMapping("/api")
public class AnexoControlador {

	private final AnexoServico anexoServico;

	public AnexoControlador(AnexoServico anexoServico) {
		this.anexoServico = anexoServico;
	}

	/** Lista os anexos da tarefa (nome, tamanho, quem enviou — sem os bytes). */
	@GetMapping("/tarefas/{tarefaId}/anexos")
	public List<Anexo> listar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long tarefaId) {
		return anexoServico.listar(usuarioLogado, tarefaId);
	}

	/** Anexa um arquivo. Envio: multipart/form-data com o campo "arquivo" (limite 10MB). */
	@PostMapping("/tarefas/{tarefaId}/anexos")
	public ResponseEntity<Anexo> enviar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long tarefaId, @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
		Anexo criado = anexoServico.enviar(usuarioLogado, tarefaId,
				arquivo.getOriginalFilename(), arquivo.getContentType(), arquivo.getBytes());
		return ResponseEntity.status(HttpStatus.CREATED).body(criado);
	}

	/** Baixa o arquivo do anexo. */
	@GetMapping("/anexos/{anexoId}/download")
	public ResponseEntity<byte[]> baixar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long anexoId) {
		Anexo anexo = anexoServico.baixar(usuarioLogado, anexoId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(anexo.getTipo()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + anexo.getNome().replace("\"", "") + "\"")
				.body(anexo.getDados());
	}

	/** Exclui um anexo (somente quem enviou ou o Gestor). */
	@DeleteMapping("/anexos/{anexoId}")
	public Map<String, String> excluir(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long anexoId) {
		anexoServico.excluir(usuarioLogado, anexoId);
		return Map.of("mensagem", "Anexo excluido.");
	}

}
