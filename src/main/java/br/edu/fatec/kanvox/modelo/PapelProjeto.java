package br.edu.fatec.kanvox.modelo;

/**
 * Papel de um usuario dentro de um projeto (RF-01.2).
 * O papel pertence ao vinculo usuario-projeto (MembroProjeto),
 * nao ao usuario — um mesmo usuario pode ser Gestor em um
 * projeto e Membro em outro.
 */
public enum PapelProjeto {
	GESTOR,
	MEMBRO,
	OBSERVADOR
}
