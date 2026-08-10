package br.edu.fatec.kanvox.servico;

/**
 * Excecao lancada quando uma regra de negocio e violada
 * (ex.: e-mail ja cadastrado, usuario sem permissao).
 * A mensagem e amigavel e pode ser exibida ao usuario final (RNF-05).
 */
public class RegraDeNegocioExcecao extends RuntimeException {

	public RegraDeNegocioExcecao(String mensagem) {
		super(mensagem);
	}

}
