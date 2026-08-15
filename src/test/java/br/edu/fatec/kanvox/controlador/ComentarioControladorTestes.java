package br.edu.fatec.kanvox.controlador;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** Testes dos comentarios nas tarefas (RF-07.1) — a devolutiva entre Membro e Gestor. */
@SpringBootTest
@AutoConfigureMockMvc
class ComentarioControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	private long cadastrar(String nome, String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"Senha123!\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	private String logar(String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"Senha123!\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	private long criarProjeto(String token, String nome) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	private void convidarEAceitar(String tokenGestor, long projetoId, String email, String papel,
			String tokenConvidado) throws Exception {
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"papel\":\"" + papel + "\"}"))
				.andExpect(status().isCreated());
		String convites = mockMvc.perform(get("/api/convites")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andReturn().getResponse().getContentAsString();
		long conviteId = ((Number) JsonPath.read(convites, "$[0].id")).longValue();
		mockMvc.perform(post("/api/convites/" + conviteId + "/aceitar")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andExpect(status().isOk());
	}

	private long criarTarefa(String tokenGestor, long projetoId, long responsavelId) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa comentada\",\"responsaveis\":[{\"id\":" + responsavelId + "}]}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	@Test
	void membroCometaNaTarefaEGestorENotificado() throws Exception {
		cadastrar("Gestor Comenta", "gestor.comenta@teste.com");
		long membroId = cadastrar("Membro Comenta", "membro.comenta@teste.com");
		String tokenGestor = logar("gestor.comenta@teste.com");
		String tokenMembro = logar("membro.comenta@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Comentarios");
		convidarEAceitar(tokenGestor, projetoId, "membro.comenta@teste.com", "MEMBRO", tokenMembro);
		long tarefaId = criarTarefa(tokenGestor, projetoId, membroId);

		// o membro escreve a devolutiva
		mockMvc.perform(post("/api/tarefas/" + tarefaId + "/comentarios")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\":\"Terminei a implementacao, pode avaliar.\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.autor.email").value("membro.comenta@teste.com"));

		// a conversa aparece para o gestor
		mockMvc.perform(get("/api/tarefas/" + tarefaId + "/comentarios")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].texto").value("Terminei a implementacao, pode avaliar."));

		// e o gestor foi notificado do comentario (RF-06.5)
		mockMvc.perform(get("/api/notificacoes")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(jsonPath("$[0].tipo").value("NOVO_COMENTARIO"));
	}

	@Test
	void observadorNaoCometaEComentarioVazioERejeitado() throws Exception {
		cadastrar("Gestor ComObs", "gestor.comobs@teste.com");
		cadastrar("Observador Com", "observador.com@teste.com");
		String tokenGestor = logar("gestor.comobs@teste.com");
		String tokenObservador = logar("observador.com@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Comentario Restrito");
		convidarEAceitar(tokenGestor, projetoId, "observador.com@teste.com", "OBSERVADOR", tokenObservador);
		long gestorId = ((Number) JsonPath.read(mockMvc.perform(get("/api/autenticacao/eu")
				.header("Authorization", "Bearer " + tokenGestor))
				.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
		long tarefaId = criarTarefa(tokenGestor, projetoId, gestorId);

		mockMvc.perform(post("/api/tarefas/" + tarefaId + "/comentarios")
				.header("Authorization", "Bearer " + tokenObservador)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\":\"Tentativa de comentario\"}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/tarefas/" + tarefaId + "/comentarios")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("O comentario nao pode ser vazio."));
	}

	@Test
	void soOAutorOuOGestorExcluemComentario() throws Exception {
		cadastrar("Gestor Excomm", "gestor.excom@teste.com");
		long membroId = cadastrar("Membro Excom", "membro.excom@teste.com");
		String tokenGestor = logar("gestor.excom@teste.com");
		String tokenMembro = logar("membro.excom@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Exclui Comentario");
		convidarEAceitar(tokenGestor, projetoId, "membro.excom@teste.com", "MEMBRO", tokenMembro);
		long tarefaId = criarTarefa(tokenGestor, projetoId, membroId);

		// o gestor comenta; o membro (nao autor, nao gestor) nao pode excluir
		String resposta = mockMvc.perform(post("/api/tarefas/" + tarefaId + "/comentarios")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\":\"Comentario do gestor\"}"))
				.andReturn().getResponse().getContentAsString();
		long comentarioId = ((Number) JsonPath.read(resposta, "$.id")).longValue();

		mockMvc.perform(delete("/api/comentarios/" + comentarioId)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/comentarios/" + comentarioId)
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/tarefas/" + tarefaId + "/comentarios")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(jsonPath("$.length()").value(0));
	}

}
