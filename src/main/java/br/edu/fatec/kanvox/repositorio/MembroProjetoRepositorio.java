package br.edu.fatec.kanvox.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.fatec.kanvox.modelo.MembroProjeto;

/** Acesso ao banco para o vinculo entre usuario e projeto. */
public interface MembroProjetoRepositorio extends JpaRepository<MembroProjeto, Long> {

	/** Vinculo ativo de um usuario com um projeto — usado nas validacoes de RBAC. */
	@Query("select m from MembroProjeto m where m.projeto.id = :projetoId and m.usuario.id = :usuarioId"
			+ " and m.situacao = br.edu.fatec.kanvox.modelo.SituacaoNoProjeto.ATIVO")
	Optional<MembroProjeto> buscarVinculoAtivo(@Param("projetoId") Long projetoId, @Param("usuarioId") Long usuarioId);

	/** Todos os membros ativos de um projeto (RF-02.3). */
	@Query("select m from MembroProjeto m where m.projeto.id = :projetoId"
			+ " and m.situacao = br.edu.fatec.kanvox.modelo.SituacaoNoProjeto.ATIVO")
	List<MembroProjeto> buscarMembrosAtivos(@Param("projetoId") Long projetoId);

	/** Membros ativos e convites pendentes de um projeto — exibidos na tela de membros. */
	@Query("select m from MembroProjeto m where m.projeto.id = :projetoId"
			+ " and m.situacao <> br.edu.fatec.kanvox.modelo.SituacaoNoProjeto.INATIVO")
	List<MembroProjeto> buscarMembrosEConvites(@Param("projetoId") Long projetoId);

	/** Convites pendentes recebidos por um usuario (RF-01.4). */
	@Query("select m from MembroProjeto m where m.usuario.id = :usuarioId"
			+ " and m.situacao = br.edu.fatec.kanvox.modelo.SituacaoNoProjeto.PENDENTE")
	List<MembroProjeto> buscarConvitesPendentes(@Param("usuarioId") Long usuarioId);

	/** Qualquer vinculo (em qualquer situacao) de um usuario com um projeto — preserva o historico (RF-01.5). */
	@Query("select m from MembroProjeto m where m.projeto.id = :projetoId and m.usuario.id = :usuarioId")
	Optional<MembroProjeto> buscarVinculo(@Param("projetoId") Long projetoId, @Param("usuarioId") Long usuarioId);

}
