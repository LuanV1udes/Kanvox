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

/** Testes do CRUD de projetos e da gestao de membros com RBAC (RF-02 e RF-01). */
@SpringBootTest
@AutoConfigureMockMvc
class ProjetoControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	/** Cadastra um usuario novo e devolve o token de login dele. */
	private String cadastrarELogar(String nome, String email) throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"Senha123!\"}"));
		String resposta = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"Senha123!\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	/** Cria um projeto e devolve o id gerado. */
	private long criarProjeto(String token, String nome) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"descricao\":\"Projeto de teste\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	/** Aceita o primeiro convite pendente do usuario e devolve o id do vinculo. */
	private long aceitarPrimeiroConvite(String tokenConvidado) throws Exception {
		String convites = mockMvc.perform(get("/api/convites")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andReturn().getResponse().getContentAsString();
		long conviteId = ((Number) JsonPath.read(convites, "$[0].id")).longValue();
		mockMvc.perform(post("/api/convites/" + conviteId + "/aceitar")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andExpect(status().isOk());
		return conviteId;
	}

	@Test
	void criarProjetoTornaOCriadorGestor() throws Exception {
		String token = cadastrarELogar("Gestora", "gestora@teste.com");
		long projetoId = criarProjeto(token, "Projeto Kanvox");

		mockMvc.perform(get("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].usuario.email").value("gestora@teste.com"))
				.andExpect(jsonPath("$[0].papelNoProjeto").value("GESTOR"));
	}

	@Test
	void visaoGeralMostraProgressoEMembros() throws Exception {
		String token = cadastrarELogar("Gestor Visao", "visao@teste.com");
		long projetoId = criarProjeto(token, "Projeto Visao Geral");

		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.projeto.nome").value("Projeto Visao Geral"))
				.andExpect(jsonPath("$.progresso").value(0))
				.andExpect(jsonPath("$.totalTarefas").value(0))
				.andExpect(jsonPath("$.membros.length()").value(1));
	}

	@Test
	void conviteFicaPendenteAteOConvidadoAceitar() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor Convite", "gestor.convite@teste.com");
		String tokenMembro = cadastrarELogar("Membro Convidado", "membro.convite@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Convite");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.convite@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.papelNoProjeto").value("MEMBRO"))
				.andExpect(jsonPath("$.situacao").value("PENDENTE"));

		// enquanto o convite esta pendente, o convidado NAO acessa o projeto
		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());

		// o convite aparece na lista dele, com o nome do projeto e de quem convidou
		mockMvc.perform(get("/api/convites")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].projeto.nome").value("Projeto Convite"))
				.andExpect(jsonPath("$[0].convidadoPor").value("Gestor Convite"));

		// depois de aceitar, o acesso e liberado
		aceitarPrimeiroConvite(tokenMembro);
		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());
	}

	@Test
	void conviteRecusadoNaoDaAcessoAoProjeto() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor Recusa", "gestor.recusa@teste.com");
		String tokenMembro = cadastrarELogar("Membro Recusa", "membro.recusa@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Recusado");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.recusa@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated());

		String convites = mockMvc.perform(get("/api/convites")
				.header("Authorization", "Bearer " + tokenMembro))
				.andReturn().getResponse().getContentAsString();
		long conviteId = ((Number) JsonPath.read(convites, "$[0].id")).longValue();

		mockMvc.perform(post("/api/convites/" + conviteId + "/recusar")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());

		// sem acesso, e o convite nao pode ser respondido de novo
		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/convites/" + conviteId + "/aceitar")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Este convite ja foi respondido."));
	}

	@Test
	void convidarEmailSemCadastroERejeitado() throws Exception {
		String token = cadastrarELogar("Gestor Sozinho", "gestor.sozinho@teste.com");
		long projetoId = criarProjeto(token, "Projeto Sem Convidado");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nao.existe@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value(
						"Nenhum usuario cadastrado com este e-mail. O convite so funciona para quem ja tem conta."));
	}

	@Test
	void membroNaoPodeEditarNemConvidar() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor RBAC", "gestor.rbac@teste.com");
		String tokenMembro = cadastrarELogar("Membro RBAC", "membro.rbac@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto RBAC");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.rbac@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated());
		aceitarPrimeiroConvite(tokenMembro);

		// membro tenta editar o projeto: 403
		mockMvc.perform(put("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Tentativa de edicao\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("Somente o Gestor do projeto pode executar esta operacao."));

		// membro tenta convidar alguem: 403 (convite e exclusivo do Gestor, RF-01.4)
		String tokenTerceiro = cadastrarELogar("Terceiro RBAC", "terceiro.rbac@teste.com");
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenMembro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"terceiro.rbac@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("Somente o Gestor do projeto pode executar esta operacao."));

		// quem nem participa do projeto tambem recebe 403
		String tokenDeFora = cadastrarELogar("Fora do Projeto", "fora.rbac@teste.com");
		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenDeFora))
				.andExpect(status().isForbidden());
	}

	@Test
	void membroPodeSairMasGestorNao() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor Saida", "gestor.saida@teste.com");
		String tokenMembro = cadastrarELogar("Membro Saida", "membro.saida@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Saida");

		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.saida@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated());
		aceitarPrimeiroConvite(tokenMembro);

		// membro sai voluntariamente e perde o acesso
		mockMvc.perform(post("/api/projetos/" + projetoId + "/sair")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());

		// gestor nao pode sair do proprio projeto
		mockMvc.perform(post("/api/projetos/" + projetoId + "/sair")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.erro").value("O Gestor nao pode sair do proprio projeto."));
	}

	@Test
	void projetoEncerradoNaoPodeSerEditado() throws Exception {
		String token = cadastrarELogar("Gestor Encerra", "gestor.encerra@teste.com");
		long projetoId = criarProjeto(token, "Projeto Encerrado");

		mockMvc.perform(put("/api/projetos/" + projetoId + "/encerrar")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ENCERRADO"));

		mockMvc.perform(put("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Novo nome\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Um projeto encerrado nao pode ser editado."));
	}

	@Test
	void quemSaiuPodeSerConvidadoDeNovo() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor Volta", "gestor.volta@teste.com");
		String tokenMembro = cadastrarELogar("Membro Volta", "membro.volta@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Volta");

		String corpoConvite = "{\"email\":\"membro.volta@teste.com\",\"papel\":\"MEMBRO\"}";
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON).content(corpoConvite))
				.andExpect(status().isCreated());
		aceitarPrimeiroConvite(tokenMembro);

		mockMvc.perform(post("/api/projetos/" + projetoId + "/sair")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());

		// convite de novo: o vinculo antigo vira um convite pendente, desta vez como observador
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.volta@teste.com\",\"papel\":\"OBSERVADOR\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.papelNoProjeto").value("OBSERVADOR"))
				.andExpect(jsonPath("$.situacao").value("PENDENTE"));

		aceitarPrimeiroConvite(tokenMembro);
		mockMvc.perform(get("/api/projetos/" + projetoId)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());
	}

}
