package br.edu.fatec.kanvox.servico;

/**
 * Excecao lancada quando o usuario esta autenticado, mas nao tem
 * permissao para a operacao (RBAC, validado na camada de servico
 * conforme o RNF-02). Convertida em resposta HTTP 403.
 */
public class PermissaoNegadaExcecao extends RuntimeException {

	public PermissaoNegadaExcecao(String mensagem) {
		super(mensagem);
	}

}
