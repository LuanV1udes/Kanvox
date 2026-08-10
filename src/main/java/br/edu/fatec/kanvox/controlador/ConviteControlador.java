package br.edu.fatec.kanvox.controlador;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.fatec.kanvox.modelo.MembroProjeto;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.ProjetoServico;

/**
 * Endpoints dos convites recebidos pelo usuario logado (RF-01.4).
 * O convite e criado pelo Gestor em POST /api/projetos/{id}/membros;
 * aqui o convidado consulta e responde (aceita ou recusa).
 */
@RestController
@RequestMapping("/api/convites")
public class ConviteControlador {

	private final ProjetoServico projetoServico;

	public ConviteControlador(ProjetoServico projetoServico) {
		this.projetoServico = projetoServico;
	}

	/** Lista os convites pendentes do usuario logado. */
	@GetMapping
	public List<Map<String, Object>> listar(@AuthenticationPrincipal Usuario usuarioLogado) {
		return projetoServico.listarConvites(usuarioLogado);
	}

	/** Aceita um convite: o usuario passa a participar do projeto. */
	@PostMapping("/{conviteId}/aceitar")
	public MembroProjeto aceitar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long conviteId) {
		return projetoServico.aceitarConvite(usuarioLogado, conviteId);
	}

	/** Recusa um convite. */
	@PostMapping("/{conviteId}/recusar")
	public Map<String, String> recusar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long conviteId) {
		projetoServico.recusarConvite(usuarioLogado, conviteId);
		return Map.of("mensagem", "Convite recusado.");
	}

}
