package br.edu.fatec.kanvox.modelo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Vinculo entre um usuario e um projeto, com o papel exercido nele (RF-01.2).
 * O convite nasce PENDENTE e o convidado decide aceitar ou recusar (RF-01.4).
 * Quando o membro recusa, sai ou e removido (RF-01.5), o vinculo e marcado
 * como INATIVO — nunca excluido — para preservar o historico do projeto.
 */
@Entity
@Table(name = "membro_projeto", uniqueConstraints = @UniqueConstraint(columnNames = { "projeto_id", "usuario_id" }))
public class MembroProjeto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// JsonIgnore: ao listar os membros de um projeto, nao ha necessidade de
	// repetir o projeto inteiro dentro de cada membro na resposta JSON
	@JsonIgnore
	@ManyToOne(optional = false)
	@JoinColumn(name = "projeto_id")
	private Projeto projeto;

	@ManyToOne(optional = false)
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PapelProjeto papelNoProjeto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SituacaoNoProjeto situacao = SituacaoNoProjeto.PENDENTE;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Projeto getProjeto() {
		return projeto;
	}

	public void setProjeto(Projeto projeto) {
		this.projeto = projeto;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

	public PapelProjeto getPapelNoProjeto() {
		return papelNoProjeto;
	}

	public void setPapelNoProjeto(PapelProjeto papelNoProjeto) {
		this.papelNoProjeto = papelNoProjeto;
	}

	public SituacaoNoProjeto getSituacao() {
		return situacao;
	}

	public void setSituacao(SituacaoNoProjeto situacao) {
		this.situacao = situacao;
	}

}
