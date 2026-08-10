package br.edu.fatec.kanvox.modelo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Usuario cadastrado na plataforma (RF-01).
 * Nao existe papel global: o papel (gestor/membro/observador)
 * e definido por projeto, na entidade MembroProjeto.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false, unique = true)
	private String email;

	// WRITE_ONLY: o cliente pode enviar a senha no cadastro,
	// mas o hash nunca aparece nas respostas JSON da API (RNF-02)
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@Column(nullable = false)
	private String senha;

	// Usados apenas na recuperacao de senha (RF-01.3); nunca expostos na API
	@JsonIgnore
	private String tokenRecuperacao;

	@JsonIgnore
	private LocalDateTime tokenExpiraEm;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getSenha() {
		return senha;
	}

	public void setSenha(String senha) {
		this.senha = senha;
	}

	public String getTokenRecuperacao() {
		return tokenRecuperacao;
	}

	public void setTokenRecuperacao(String tokenRecuperacao) {
		this.tokenRecuperacao = tokenRecuperacao;
	}

	public LocalDateTime getTokenExpiraEm() {
		return tokenExpiraEm;
	}

	public void setTokenExpiraEm(LocalDateTime tokenExpiraEm) {
		this.tokenExpiraEm = tokenExpiraEm;
	}

}
