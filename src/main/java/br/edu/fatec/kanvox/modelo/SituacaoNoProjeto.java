package br.edu.fatec.kanvox.modelo;

/**
 * Situacao do vinculo entre usuario e projeto (RF-01.4/RF-01.5).
 * O convite nasce PENDENTE e so vira ATIVO quando o convidado aceita;
 * recusar, sair ou ser removido torna o vinculo INATIVO — o registro
 * nunca e excluido, preservando o historico.
 */
public enum SituacaoNoProjeto {
	PENDENTE,
	ATIVO,
	INATIVO
}
