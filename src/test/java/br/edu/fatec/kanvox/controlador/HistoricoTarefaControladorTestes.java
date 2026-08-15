package br.edu.fatec.kanvox.controlador;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * Testes da linha do tempo de atividade da tarefa: os registros sao gerados
 * automaticamente pelo TarefaServico (criacao, edicao, mudanca de coluna) —
 * nao existe escrita pelo usuario, so leitura.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HistoricoTarefaControladorTestes {

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

	private long criarTarefa(String token, long projetoId, String corpoJson) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpoJson))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	@Test
	void criarTarefaRegistraOEventoDeCriacao() throws Exception {
		cadastrar("Gestor Historico", "gestor.historico@teste.com");
		String token = logar("gestor.historico@teste.com");
		long projetoId = criarProjeto(token, "Projeto Historico");
		long tarefaId = criarTarefa(token, projetoId, "{\"titulo\":\"Tarefa nova\"}");

		mockMvc.perform(get("/api/tarefas/" + tarefaId + "/historico")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].descricao").value("Criou a tarefa."))
				.andExpect(jsonPath("$[0].autor.email").value("gestor.historico@teste.com"));
	}

	@Test
	void moverDeColunaRegistraOEventoNaLinhaDoTempo() throws Exception {
		cadastrar("Gestor Move Hist", "gestor.movehist@teste.com");
		String token = logar("gestor.movehist@teste.com");
		long projetoId = criarProjeto(token, "Projeto Move Historico");
		long tarefaId = criarTarefa(token, projetoId, "{\"titulo\":\"Tarefa a mover\"}");

		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isOk());

		// mais recente primeiro: a movimentacao aparece antes da criacao
		mockMvc.perform(get("/api/tarefas/" + tarefaId + "/historico")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].descricao").value(containsString("A Fazer")))
				.andExpect(jsonPath("$[0].descricao").value(containsString("Em Andamento")))
				.andExpect(jsonPath("$[1].descricao").value("Criou a tarefa."));
	}

	@Test
	void editarTarefaRegistraResumoDosCamposAlterados() throws Exception {
		cadastrar("Gestor Edita Hist", "gestor.editahist@teste.com");
		String token = logar("gestor.editahist@teste.com");
		long projetoId = criarProjeto(token, "Projeto Edita Historico");
		long tarefaId = criarTarefa(token, projetoId, "{\"titulo\":\"Titulo original\",\"prioridade\":\"MEDIA\"}");

		mockMvc.perform(put("/api/tarefas/" + tarefaId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Titulo novo\",\"prioridade\":\"ALTA\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/tarefas/" + tarefaId + "/historico")
				.header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$[0].descricao").value(containsString("título")))
				.andExpect(jsonPath("$[0].descricao").value(containsString("prioridade")));
	}

	@Test
	void observadorLeOHistoricoMasNaoAlteraATarefa() throws Exception {
		cadastrar("Gestor Hist Obs", "gestor.histobs@teste.com");
		cadastrar("Observador Hist", "observador.hist@teste.com");
		String tokenGestor = logar("gestor.histobs@teste.com");
		String tokenObservador = logar("observador.hist@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Historico Observador");
		convidarEAceitar(tokenGestor, projetoId, "observador.hist@teste.com", "OBSERVADOR", tokenObservador);
		long tarefaId = criarTarefa(tokenGestor, projetoId, "{\"titulo\":\"Tarefa visivel ao observador\"}");

		mockMvc.perform(get("/api/tarefas/" + tarefaId + "/historico")
				.header("Authorization", "Bearer " + tokenObservador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void excluirTarefaComHistoricoNaoQuebraPorChaveEstrangeira() throws Exception {
		cadastrar("Gestor Exclui Hist", "gestor.excluihist@teste.com");
		String token = logar("gestor.excluihist@teste.com");
		long projetoId = criarProjeto(token, "Projeto Exclui Historico");
		long tarefaId = criarTarefa(token, projetoId, "{\"titulo\":\"Tarefa com historico\"}");

		// gera mais um evento de historico antes de excluir (criacao + movimentacao)
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/tarefas/" + tarefaId)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

}
