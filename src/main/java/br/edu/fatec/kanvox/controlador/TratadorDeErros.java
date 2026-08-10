package br.edu.fatec.kanvox.controlador;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.edu.fatec.kanvox.servico.PermissaoNegadaExcecao;
import br.edu.fatec.kanvox.servico.RegraDeNegocioExcecao;

/**
 * Converte excecoes em respostas JSON amigaveis (RNF-05):
 * o usuario final recebe uma mensagem clara, e os detalhes
 * tecnicos vao apenas para o log (RNF-03).
 */
@RestControllerAdvice
public class TratadorDeErros {

	private static final Logger registrador = LoggerFactory.getLogger(TratadorDeErros.class);

	@ExceptionHandler(RegraDeNegocioExcecao.class)
	public ResponseEntity<Map<String, String>> tratarRegraDeNegocio(RegraDeNegocioExcecao excecao) {
		return ResponseEntity.badRequest().body(Map.of("erro", excecao.getMessage()));
	}

	/** Usuario autenticado, mas sem permissao para a operacao (RBAC): devolve 403. */
	@ExceptionHandler(PermissaoNegadaExcecao.class)
	public ResponseEntity<Map<String, String>> tratarPermissaoNegada(PermissaoNegadaExcecao excecao) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", excecao.getMessage()));
	}

	/** Rota que nao existe: devolve 404 em vez de cair no erro generico 500. */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Map<String, String>> tratarRotaInexistente(NoResourceFoundException excecao) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Rota nao encontrada."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> tratarErroInesperado(Exception excecao) {
		// Excecoes do proprio Spring (metodo nao suportado, corpo mal formado...)
		// ja carregam o status HTTP correto — repassamos em vez de responder 500
		if (excecao instanceof ErrorResponse respostaDeErro) {
			return ResponseEntity.status(respostaDeErro.getStatusCode())
					.body(Map.of("erro", "Requisicao invalida."));
		}
		registrador.error("Erro inesperado ao processar a requisicao", excecao);
		return ResponseEntity.internalServerError()
				.body(Map.of("erro", "Ocorreu um erro inesperado. Tente novamente em instantes."));
	}

}
