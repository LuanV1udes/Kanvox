package br.edu.fatec.kanvox.controlador;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.DesempenhoServico;

/** Painel de desempenho do projeto: carga de trabalho e eficiencia de entrega da equipe. */
@RestController
@RequestMapping("/api")
public class DesempenhoControlador {

	private final DesempenhoServico desempenhoServico;

	public DesempenhoControlador(DesempenhoServico desempenhoServico) {
		this.desempenhoServico = desempenhoServico;
	}

	/** Gera o painel de desempenho do projeto — somente o Gestor pode consultar. */
	@GetMapping("/projetos/{projetoId}/desempenho")
	public Map<String, Object> gerar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId) {
		return desempenhoServico.gerar(usuarioLogado, projetoId);
	}

}
