package br.edu.fatec.kanvox.controlador;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import br.edu.fatec.kanvox.servico.NotificacaoServico;

/** Testes das notificacoes internas (RF-06). */
@SpringBootTest
@AutoConfigureMockMvc
class NotificacaoControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NotificacaoServico notificacaoServico;

	private long cadastrar(String nome, String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"123456\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	private String logar(String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"123456\"}"))
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

	/** Convida e aceita em nome do convidado (o aceite gera notificacoes de convite, RF-01.4). */
	private void convidarEAceitar(String tokenGestor, long projetoId, String email, String tokenConvidado) throws Exception {
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated());
		String convites = mockMvc.perform(get("/api/convites")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andReturn().getResponse().getContentAsString();
		long conviteId = ((Number) JsonPath.read(convites, "$[0].id")).longValue();
		mockMvc.perform(post("/api/convites/" + conviteId + "/aceitar")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andExpect(status().isOk());
	}

	@Test
	void tarefaAtribuidaPeloGestorNotificaOMembro() throws Exception {
		cadastrar("Gestor Atribui", "gestor.atribui@teste.com");
		long membroId = cadastrar("Membro Atribui", "membro.atribui@teste.com");
		String tokenGestor = logar("gestor.atribui@teste.com");
		String tokenMembro = logar("membro.atribui@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Atribuicao");
		convidarEAceitar(tokenGestor, projetoId, "membro.atribui@teste.com", tokenMembro);

		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa delegada\",\"responsavel\":{\"id\":" + membroId + "}}"))
				.andExpect(status().isCreated());

		// o membro recebeu a notificacao de atribuicao (RF-06.3) — a mais recente da lista
		mockMvc.perform(get("/api/notificacoes")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].tipo").value("TAREFA_ATRIBUIDA"))
				.andExpect(jsonPath("$[0].lida").value(false));

		// o gestor nao recebeu aviso de atribuicao (foi ele quem atribuiu)
		mockMvc.perform(get("/api/notificacoes")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(jsonPath("$[?(@.tipo == 'TAREFA_ATRIBUIDA')]").isEmpty());
	}

	@Test
	void tarefaBloqueadaPeloMembroNotificaOGestor() throws Exception {
		cadastrar("Gestor Bloqueio", "gestor.bloqueio@teste.com");
		long membroId = cadastrar("Membro Bloqueio", "membro.bloqueio@teste.com");
		String tokenGestor = logar("gestor.bloqueio@teste.com");
		String tokenMembro = logar("membro.bloqueio@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Bloqueio");
		convidarEAceitar(tokenGestor, projetoId, "membro.bloqueio@teste.com", tokenMembro);

		// o gestor cria a tarefa atribuida ao membro (criacao e exclusiva do Gestor)
		String resposta = mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa travada\",\"responsavel\":{\"id\":" + membroId + "}}"))
				.andReturn().getResponse().getContentAsString();
		long tarefaId = ((Number) JsonPath.read(resposta, "$.id")).longValue();

		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"BLOQUEADO\"}"))
				.andExpect(status().isOk());

		// o gestor foi avisado do bloqueio (RF-06.1) — a notificacao mais recente
		mockMvc.perform(get("/api/notificacoes")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(jsonPath("$[0].tipo").value("TAREFA_BLOQUEADA"));
	}

	@Test
	void marcarComoLidaSoFuncionaParaODono() throws Exception {
		cadastrar("Gestor Leitura", "gestor.leitura@teste.com");
		long membroId = cadastrar("Membro Leitura", "membro.leitura@teste.com");
		String tokenGestor = logar("gestor.leitura@teste.com");
		String tokenMembro = logar("membro.leitura@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Leitura");
		convidarEAceitar(tokenGestor, projetoId, "membro.leitura@teste.com", tokenMembro);

		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa notificada\",\"responsavel\":{\"id\":" + membroId + "}}"))
				.andExpect(status().isCreated());

		String notificacoes = mockMvc.perform(get("/api/notificacoes")
				.header("Authorization", "Bearer " + tokenMembro))
				.andReturn().getResponse().getContentAsString();
		long notificacaoId = ((Number) JsonPath.read(notificacoes, "$[0].id")).longValue();

		// outro usuario nao pode marcar a notificacao alheia
		mockMvc.perform(put("/api/notificacoes/" + notificacaoId + "/lida")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isForbidden());

		// o dono marca normalmente
		mockMvc.perform(put("/api/notificacoes/" + notificacaoId + "/lida")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lida").value(true));
	}

	@Test
	void rotinaDePrazoVencidoNotificaOGestorUmaUnicaVez() throws Exception {
		cadastrar("Gestor Prazo", "gestor.prazo@teste.com");
		String tokenGestor = logar("gestor.prazo@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Prazo Vencido");

		String ontem = LocalDate.now().minusDays(1).toString();
		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa atrasada\",\"prazo\":\"" + ontem + "\"}"))
				.andExpect(status().isCreated());

		// executa a rotina agendada manualmente (em producao roda a cada 30 min)
		notificacaoServico.notificarTarefasAtrasadas();

		mockMvc.perform(get("/api/notificacoes")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].tipo").value("PRAZO_VENCIDO"));

		// rodar de novo nao duplica a notificacao (controle notificadoAtraso)
		notificacaoServico.notificarTarefasAtrasadas();
		mockMvc.perform(get("/api/notificacoes")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(jsonPath("$.length()").value(1));
	}

}
