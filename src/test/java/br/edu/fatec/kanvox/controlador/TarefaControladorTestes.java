package br.edu.fatec.kanvox.controlador;

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
 * Testes do quadro Kanban (RF-03) — modulo com cobertura obrigatoria
 * segundo o documento do projeto. O foco e nas regras de permissao:
 * Gestor tudo, Membro so as proprias tarefas, Observador so leitura.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TarefaControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	/** Cadastra um usuario e devolve o id gerado. */
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
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	/** Convida e ja aceita o convite em nome do convidado (RF-01.4). */
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
	void gestorCriaTarefaParaMembroETarefaNasceEmAFazer() throws Exception {
		cadastrar("Gestor Kanban", "gestor.kanban@teste.com");
		long membroId = cadastrar("Membro Kanban", "membro.kanban@teste.com");
		String tokenGestor = logar("gestor.kanban@teste.com");
		String tokenMembro = logar("membro.kanban@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Kanban");
		convidarEAceitar(tokenGestor, projetoId, "membro.kanban@teste.com", "MEMBRO", tokenMembro);

		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Configurar banco\",\"descricao\":\"Criar tabelas\",\"prazo\":\"2026-08-01\",\"responsavel\":{\"id\":" + membroId + "}}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("A_FAZER"))
				.andExpect(jsonPath("$.responsavel.email").value("membro.kanban@teste.com"));

		// o polling do quadro lista a tarefa (RF-03.6)
		mockMvc.perform(get("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].titulo").value("Configurar banco"));
	}

	@Test
	void membroNaoPodeCriarTarefas() throws Exception {
		cadastrar("Gestor Auto", "gestor.auto@teste.com");
		cadastrar("Membro Auto", "membro.auto@teste.com");
		String tokenGestor = logar("gestor.auto@teste.com");
		String tokenMembro = logar("membro.auto@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Criacao Restrita");
		convidarEAceitar(tokenGestor, projetoId, "membro.auto@teste.com", "MEMBRO", tokenMembro);

		// criar tarefas e exclusivo do Gestor (RF-03.2)
		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa indevida\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("Somente o Gestor do projeto pode criar tarefas."));
	}

	@Test
	void observadorTemAcessoSomenteLeitura() throws Exception {
		cadastrar("Gestor Obs", "gestor.obs@teste.com");
		cadastrar("Observador", "observador.obs@teste.com");
		String tokenGestor = logar("gestor.obs@teste.com");
		String tokenObservador = logar("observador.obs@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Observado");
		convidarEAceitar(tokenGestor, projetoId, "observador.obs@teste.com", "OBSERVADOR", tokenObservador);
		long tarefaId = criarTarefa(tokenGestor, projetoId, "{\"titulo\":\"Tarefa visivel\"}");

		// observador ve o quadro normalmente
		mockMvc.perform(get("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenObservador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		// mas nao cria nem move tarefas
		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenObservador)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Nao deveria existir\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + tokenObservador)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void membroNuncaEditaConteudoMasMovimentaAPropriaTarefa() throws Exception {
		long gestorId = cadastrar("Gestor Dono", "gestor.dono@teste.com");
		long membroId = cadastrar("Membro Dono", "membro.dono@teste.com");
		String tokenGestor = logar("gestor.dono@teste.com");
		String tokenMembro = logar("membro.dono@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Dono da Tarefa");
		convidarEAceitar(tokenGestor, projetoId, "membro.dono@teste.com", "MEMBRO", tokenMembro);

		// tarefa do gestor: membro nao pode editar (nem mover, pois nao e o responsavel)
		long tarefaDoGestor = criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa do gestor\",\"responsavel\":{\"id\":" + gestorId + "}}");
		mockMvc.perform(put("/api/tarefas/" + tarefaDoGestor)
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Invasao\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("Somente o Gestor do projeto pode executar esta operacao."));

		// tarefa atribuida a ele pelo gestor: membro MOVIMENTA normalmente...
		long tarefaDoMembro = criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa do membro\",\"responsavel\":{\"id\":" + membroId + "}}");
		mockMvc.perform(put("/api/tarefas/" + tarefaDoMembro + "/status")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));

		// ...mas NAO edita o conteudo dela — editar e exclusivo do Gestor, mesmo na propria tarefa
		mockMvc.perform(put("/api/tarefas/" + tarefaDoMembro)
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tentativa de editar a propria tarefa\",\"prazo\":\"2026-09-15\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("Somente o Gestor do projeto pode executar esta operacao."));

		// o Gestor edita normalmente, inclusive a tarefa do membro
		mockMvc.perform(put("/api/tarefas/" + tarefaDoMembro)
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa do membro atualizada pelo gestor\",\"prazo\":\"2026-09-15\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("Tarefa do membro atualizada pelo gestor"))
				.andExpect(jsonPath("$.prazo").value("2026-09-15"));
	}

	@Test
	void moverParaStatusInvalidoERejeitado() throws Exception {
		cadastrar("Gestor Status", "gestor.status@teste.com");
		String token = logar("gestor.status@teste.com");
		long projetoId = criarProjeto(token, "Projeto Status");
		long tarefaId = criarTarefa(token, projetoId, "{\"titulo\":\"Tarefa movel\"}");

		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"COLUNA_INEXISTENTE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Status invalido. Use A_FAZER, EM_ANDAMENTO, BLOQUEADO, EM_REVISAO ou CONCLUIDO."));
	}

	@Test
	void somenteGestorExcluiTarefas() throws Exception {
		cadastrar("Gestor Exclui", "gestor.exclui@teste.com");
		long membroId = cadastrar("Membro Exclui", "membro.exclui@teste.com");
		String tokenGestor = logar("gestor.exclui@teste.com");
		String tokenMembro = logar("membro.exclui@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Exclusao");
		convidarEAceitar(tokenGestor, projetoId, "membro.exclui@teste.com", "MEMBRO", tokenMembro);

		// nem mesmo a propria tarefa o membro pode excluir (RF-03.2)
		long tarefaDoMembro = criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa a excluir\",\"responsavel\":{\"id\":" + membroId + "}}");
		mockMvc.perform(delete("/api/tarefas/" + tarefaDoMembro)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/tarefas/" + tarefaDoMembro)
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void responsavelPrecisaSerMembroDoProjeto() throws Exception {
		cadastrar("Gestor Valida", "gestor.valida@teste.com");
		long usuarioDeForaId = cadastrar("Usuario De Fora", "fora.valida@teste.com");
		String tokenGestor = logar("gestor.valida@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Validacao");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa orfã\",\"responsavel\":{\"id\":" + usuarioDeForaId + "}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("O responsavel precisa ser membro ativo do projeto."));
	}

	@Test
	void membroEntregaEmRevisaoESomenteGestorConclui() throws Exception {
		cadastrar("Gestor Revisa", "gestor.revisa@teste.com");
		long membroId = cadastrar("Membro Revisa", "membro.revisa@teste.com");
		String tokenGestor = logar("gestor.revisa@teste.com");
		String tokenMembro = logar("membro.revisa@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Revisao");
		convidarEAceitar(tokenGestor, projetoId, "membro.revisa@teste.com", "MEMBRO", tokenMembro);
		long tarefaId = criarTarefa(tokenGestor, projetoId,
				"{\"titulo\":\"Tarefa para entregar\",\"responsavel\":{\"id\":" + membroId + "}}");

		// o membro entrega: move a propria tarefa para Em Revisao (RF-03.7)
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_REVISAO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EM_REVISAO"));

		// mas nao pode concluir — isso e decisao do Gestor
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONCLUIDO\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value(
						"Somente o Gestor conclui tarefas. Mova para 'Em Revisao' e aguarde a avaliacao."));

		// o gestor avalia e conclui
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONCLUIDO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONCLUIDO"));

		// depois de concluida, o membro nao pode reabrir
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ANDAMENTO\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void tarefaTemPrioridadeComPadraoMedia() throws Exception {
		cadastrar("Gestor Prioriza", "gestor.prioriza@teste.com");
		String token = logar("gestor.prioriza@teste.com");
		long projetoId = criarProjeto(token, "Projeto Prioridades");

		// sem informar prioridade, nasce MEDIA
		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa comum\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.prioridade").value("MEDIA"));

		// prioridade informada na criacao e alterada na edicao
		long tarefaId = criarTarefa(token, projetoId,
				"{\"titulo\":\"Tarefa urgente\",\"prioridade\":\"ALTA\"}");
		mockMvc.perform(put("/api/tarefas/" + tarefaId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa urgente\",\"prioridade\":\"BAIXA\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.prioridade").value("BAIXA"));
	}

	@Test
	void projetoEncerradoPermiteLeituraMasNaoEscrita() throws Exception {
		cadastrar("Gestor Fim", "gestor.fim@teste.com");
		String token = logar("gestor.fim@teste.com");
		long projetoId = criarProjeto(token, "Projeto Finalizado");
		long tarefaId = criarTarefa(token, projetoId, "{\"titulo\":\"Ultima tarefa\"}");

		mockMvc.perform(put("/api/projetos/" + projetoId + "/encerrar")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		// leitura continua funcionando
		mockMvc.perform(get("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		// escrita e bloqueada
		mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa tardia\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/tarefas/" + tarefaId + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONCLUIDO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("O projeto esta encerrado: as tarefas nao podem mais ser alteradas."));
	}

}
