package br.edu.fatec.kanvox.modelo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Arquivo anexado a uma tarefa (RF-07.2) — a devolutiva em arquivo:
 * o Membro anexa a entrega e o Gestor baixa para avaliar.
 * O conteudo fica no proprio banco (simplicidade + custo zero, RNF-07),
 * com limite de 10MB por arquivo validado no servico.
 */
@Entity
@Table(name = "anexo")
public class Anexo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// JsonIgnore: os anexos sempre sao consultados dentro de uma tarefa
	@JsonIgnore
	@ManyToOne(optional = false)
	@JoinColumn(name = "tarefa_id")
	private Tarefa tarefa;

	@Column(nullable = false)
	private String nome;

	/** Tipo do arquivo (ex. application/pdf) — usado no download. */
	@Column(nullable = false)
	private String tipo;

	@Column(nullable = false)
	private long tamanho;

	// JsonIgnore: os bytes nunca vao no JSON — o download tem endpoint proprio.
	// Tipo bytea: funciona igual no PostgreSQL e no H2 dos testes (modo PostgreSQL)
	@JsonIgnore
	@Column(nullable = false, columnDefinition = "bytea")
	private byte[] dados;

	@ManyToOne(optional = false)
	@JoinColumn(name = "enviado_por_id")
	private Usuario enviadoPor;

	@Column(nullable = false)
	private LocalDateTime criadoEm = LocalDateTime.now();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Tarefa getTarefa() {
		return tarefa;
	}

	public void setTarefa(Tarefa tarefa) {
		this.tarefa = tarefa;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getTipo() {
		return tipo;
	}

	public void setTipo(String tipo) {
		this.tipo = tipo;
	}

	public long getTamanho() {
		return tamanho;
	}

	public void setTamanho(long tamanho) {
		this.tamanho = tamanho;
	}

	public byte[] getDados() {
		return dados;
	}

	public void setDados(byte[] dados) {
		this.dados = dados;
	}

	public Usuario getEnviadoPor() {
		return enviadoPor;
	}

	public void setEnviadoPor(Usuario enviadoPor) {
		this.enviadoPor = enviadoPor;
	}

	public LocalDateTime getCriadoEm() {
		return criadoEm;
	}

	public void setCriadoEm(LocalDateTime criadoEm) {
		this.criadoEm = criadoEm;
	}

}
