package br.edu.fatec.kanvox.modelo;

/**
 * Colunas do quadro Kanban (RF-03.1).
 * EM_REVISAO e a etapa de devolutiva: o Membro entrega a tarefa ali
 * e somente o Gestor a move para CONCLUIDO apos avaliar (RF-03.7).
 */
public enum StatusTarefa {
	A_FAZER,
	EM_ANDAMENTO,
	BLOQUEADO,
	EM_REVISAO,
	CONCLUIDO
}
