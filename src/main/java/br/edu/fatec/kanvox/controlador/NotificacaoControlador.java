package br.edu.fatec.kanvox.controlador;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.fatec.kanvox.modelo.Notificacao;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.NotificacaoServico;

/**
 * Endpoints de notificacoes internas (RF-06.4).
 * O frontend consulta a listagem no mesmo ciclo de polling do Kanban.
 */
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoControlador {

	private final NotificacaoServico notificacaoServico;

	public NotificacaoControlador(NotificacaoServico notificacaoServico) {
		this.notificacaoServico = notificacaoServico;
	}

	/** Lista as notificacoes do usuario logado, das mais recentes para as mais antigas. */
	@GetMapping
	public List<Notificacao> listar(@AuthenticationPrincipal Usuario usuarioLogado) {
		return notificacaoServico.listarDoUsuario(usuarioLogado);
	}

	/** Marca uma notificacao como lida. */
	@PutMapping("/{notificacaoId}/lida")
	public Notificacao marcarComoLida(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long notificacaoId) {
		return notificacaoServico.marcarComoLida(usuarioLogado, notificacaoId);
	}

	/** Marca todas as notificacoes do usuario logado como lidas. */
	@PutMapping("/lidas")
	public Map<String, String> marcarTodasComoLidas(@AuthenticationPrincipal Usuario usuarioLogado) {
		notificacaoServico.marcarTodasComoLidas(usuarioLogado);
		return Map.of("mensagem", "Todas as notificacoes foram marcadas como lidas.");
	}

}
