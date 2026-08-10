package br.edu.fatec.kanvox.controlador;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.edu.fatec.kanvox.modelo.Relatorio;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.RelatorioServico;

/**
 * Endpoints de relatorios (RF-04) e transcricao de audio (RF-05).
 * Fluxo com revisao humana (RF-05.5):
 * 1. o Gestor grava o audio e envia para /transcricoes — recebe o texto de volta
 * 2. revisa/edita o texto na tela
 * 3. gera o relatorio em /relatorios, enviando o texto revisado (opcional)
 */
@RestController
@RequestMapping("/api")
public class RelatorioControlador {

	private final RelatorioServico relatorioServico;

	public RelatorioControlador(RelatorioServico relatorioServico) {
		this.relatorioServico = relatorioServico;
	}

	/**
	 * Transcreve um audio gravado na plataforma (RF-05.1/RF-05.2).
	 * Envio: multipart/form-data com o campo "audio".
	 * Devolve { "texto": "..." } para o Gestor revisar — nada e salvo aqui.
	 */
	@PostMapping("/projetos/{projetoId}/transcricoes")
	public Map<String, String> transcrever(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId, @RequestParam("audio") MultipartFile audio) throws IOException {
		String texto = relatorioServico.transcreverAudio(usuarioLogado, projetoId,
				audio.getBytes(), audio.getOriginalFilename());
		return Map.of("texto", texto);
	}

	/**
	 * Gera um relatorio do estado atual do projeto (RF-04.1/RF-04.3).
	 * Corpo opcional: { "transcricaoAudio": "texto ja revisado pelo Gestor" }
	 */
	@PostMapping("/projetos/{projetoId}/relatorios")
	public ResponseEntity<Relatorio> gerar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId, @RequestBody(required = false) Map<String, String> corpo) {
		String transcricaoRevisada = corpo == null ? null : corpo.get("transcricaoAudio");
		Relatorio relatorio = relatorioServico.gerar(usuarioLogado, projetoId, transcricaoRevisada);
		return ResponseEntity.status(HttpStatus.CREATED).body(relatorio);
	}

	/** Lista os relatorios do projeto, do mais recente para o mais antigo (RF-04.4). */
	@GetMapping("/projetos/{projetoId}/relatorios")
	public List<Relatorio> listar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId) {
		return relatorioServico.listar(usuarioLogado, projetoId);
	}

}
