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

import br.edu.fatec.kanvox.modelo.MembroProjeto;
import br.edu.fatec.kanvox.modelo.Projeto;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.servico.ProjetoServico;

/**
 * Endpoints de projetos e membros (RF-02 e parte do RF-01).
 * O usuario logado vem do token JWT, injetado pelo Spring Security
 * via @AuthenticationPrincipal (foi colocado la pelo FiltroAutenticacaoJwt).
 */
@RestController
@RequestMapping("/api/projetos")
public class ProjetoControlador {

	private final ProjetoServico projetoServico;

	public ProjetoControlador(ProjetoServico projetoServico) {
		this.projetoServico = projetoServico;
	}

	/** Cria um projeto. Corpo esperado: { "nome": "...", "descricao": "..." } */
	@PostMapping
	public ResponseEntity<Projeto> criar(@AuthenticationPrincipal Usuario usuarioLogado,
			@RequestBody Projeto projeto) {
		Projeto criado = projetoServico.criar(usuarioLogado, projeto);
		return ResponseEntity.status(HttpStatus.CREATED).body(criado);
	}

	/** Lista os projetos em que o usuario logado participa. */
	@GetMapping
	public List<Projeto> listar(@AuthenticationPrincipal Usuario usuarioLogado) {
		return projetoServico.listarDoUsuario(usuarioLogado);
	}

	/** Visao geral do projeto: dados, membros, progresso e tarefas em aberto (RF-02.3). */
	@GetMapping("/{projetoId}")
	public Map<String, Object> buscarVisaoGeral(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId) {
		return projetoServico.buscarVisaoGeral(usuarioLogado, projetoId);
	}

	/** Edita nome e descricao. Corpo esperado: { "nome": "...", "descricao": "..." } */
	@PutMapping("/{projetoId}")
	public Projeto editar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId, @RequestBody Projeto projeto) {
		return projetoServico.editar(usuarioLogado, projetoId, projeto);
	}

	/** Encerra o projeto (RF-02.2). */
	@PutMapping("/{projetoId}/encerrar")
	public Projeto encerrar(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId) {
		return projetoServico.encerrar(usuarioLogado, projetoId);
	}

	/** Lista os membros ativos do projeto. */
	@GetMapping("/{projetoId}/membros")
	public List<MembroProjeto> listarMembros(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId) {
		return projetoServico.listarMembros(usuarioLogado, projetoId);
	}

	/** Convida um usuario ja cadastrado. Corpo esperado: { "email": "...", "papel": "MEMBRO" ou "OBSERVADOR" } */
	@PostMapping("/{projetoId}/membros")
	public ResponseEntity<MembroProjeto> convidarMembro(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId, @RequestBody Map<String, String> corpo) {
		MembroProjeto convidado = projetoServico.convidarMembro(usuarioLogado, projetoId,
				corpo.get("email"), corpo.get("papel"));
		return ResponseEntity.status(HttpStatus.CREATED).body(convidado);
	}

	/** Remove um membro do projeto (somente o Gestor). */
	@DeleteMapping("/{projetoId}/membros/{usuarioId}")
	public Map<String, String> removerMembro(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId, @PathVariable Long usuarioId) {
		projetoServico.removerMembro(usuarioLogado, projetoId, usuarioId);
		return Map.of("mensagem", "Membro removido do projeto.");
	}

	/** O usuario logado sai voluntariamente do projeto (RF-01.5). */
	@PostMapping("/{projetoId}/sair")
	public Map<String, String> sairDoProjeto(@AuthenticationPrincipal Usuario usuarioLogado,
			@PathVariable Long projetoId) {
		projetoServico.sairDoProjeto(usuarioLogado, projetoId);
		return Map.of("mensagem", "Voce saiu do projeto.");
	}

}
