package br.edu.fatec.kanvox.servico;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import br.edu.fatec.kanvox.modelo.Projeto;
import br.edu.fatec.kanvox.modelo.Relatorio;
import br.edu.fatec.kanvox.modelo.StatusTarefa;
import br.edu.fatec.kanvox.modelo.Tarefa;
import br.edu.fatec.kanvox.modelo.Usuario;
import br.edu.fatec.kanvox.repositorio.RelatorioRepositorio;
import br.edu.fatec.kanvox.repositorio.TarefaRepositorio;
import br.edu.fatec.kanvox.servico.transcricao.ServicoTranscricao;

/**
 * Regras de negocio dos relatorios (RF-04) e da transcricao de audio (RF-05).
 * O relatorio e gerado automaticamente a partir do estado atual das tarefas;
 * a narracao do Gestor (transcrita pelo Whisper e revisada por ele) entra
 * como observacao opcional.
 */
@Service
@Transactional
public class RelatorioServico {

	private static final DateTimeFormatter FORMATO_DE_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter FORMATO_DE_DATA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final RelatorioRepositorio relatorioRepositorio;
	private final TarefaRepositorio tarefaRepositorio;
	private final ProjetoServico projetoServico;
	private final ServicoTranscricao servicoTranscricao;

	public RelatorioServico(RelatorioRepositorio relatorioRepositorio,
			TarefaRepositorio tarefaRepositorio,
			ProjetoServico projetoServico,
			ServicoTranscricao servicoTranscricao) {
		this.relatorioRepositorio = relatorioRepositorio;
		this.tarefaRepositorio = tarefaRepositorio;
		this.projetoServico = projetoServico;
		this.servicoTranscricao = servicoTranscricao;
	}

	/**
	 * Transcreve um audio gravado pelo Gestor na plataforma (RF-05.1/RF-05.2).
	 * Nada e salvo aqui: o texto volta para o Gestor revisar e editar antes
	 * de compor o relatorio (RF-05.5 — human-in-the-loop).
	 */
	public String transcreverAudio(Usuario usuarioLogado, Long projetoId, byte[] audio, String nomeDoArquivo) {
		projetoServico.validarGestor(projetoId, usuarioLogado);
		if (audio == null || audio.length == 0) {
			throw new RegraDeNegocioExcecao("Nenhum audio foi enviado.");
		}
		return servicoTranscricao.transcrever(audio, nomeDoArquivo);
	}

	/**
	 * Gera um relatorio a partir do estado atual das tarefas (RF-04.1) —
	 * somente o Gestor, a qualquer momento (RF-04.3). A transcricao revisada
	 * e opcional (RF-05.3): se a transcricao falhou ou nao houve narracao,
	 * o relatorio e gerado normalmente sem ela (RF-05.4).
	 */
	public Relatorio gerar(Usuario usuarioLogado, Long projetoId, String transcricaoRevisada) {
		Projeto projeto = projetoServico.validarGestor(projetoId, usuarioLogado).getProjeto();
		List<Tarefa> tarefas = tarefaRepositorio.buscarPorProjeto(projetoId);

		// "periodo" = desde o relatorio anterior (ou desde a criacao do projeto, no primeiro)
		LocalDateTime inicioDoPeriodo = relatorioRepositorio.buscarPorProjeto(projetoId).stream()
				.findFirst()
				.map(Relatorio::getGeradoEm)
				.orElse(projeto.getCriadoEm());

		Relatorio relatorio = new Relatorio();
		relatorio.setProjeto(projeto);
		relatorio.setGeradoPor(usuarioLogado);
		relatorio.setConteudo(montarConteudo(projeto, tarefas, inicioDoPeriodo));
		if (transcricaoRevisada != null && !transcricaoRevisada.isBlank()) {
			relatorio.setTranscricaoAudio(transcricaoRevisada.trim());
		}
		return relatorioRepositorio.save(relatorio);
	}

	/** Lista os relatorios do projeto, do mais recente para o mais antigo — qualquer membro ativo (RF-04.4). */
	public List<Relatorio> listar(Usuario usuarioLogado, Long projetoId) {
		projetoServico.buscarVinculoObrigatorio(projetoId, usuarioLogado);
		return relatorioRepositorio.buscarPorProjeto(projetoId);
	}

	/**
	 * Gera o PDF de um relatorio ja existente (RF-04.4 — exportacao desejavel).
	 * Qualquer membro ativo do projeto pode baixar, a mesma regra de listar().
	 */
	public byte[] gerarPdf(Usuario usuarioLogado, Long relatorioId) {
		Relatorio relatorio = relatorioRepositorio.findById(relatorioId)
				.orElseThrow(() -> new RegraDeNegocioExcecao("Relatorio nao encontrado."));
		projetoServico.buscarVinculoObrigatorio(relatorio.getProjeto().getId(), usuarioLogado);
		return montarPdf(relatorio);
	}

	private byte[] montarPdf(Relatorio relatorio) {
		Font fonteTitulo = new Font(Font.HELVETICA, 14, Font.BOLD);
		Font fonteSubtitulo = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);
		Font fonteCorpo = new Font(Font.HELVETICA, 10);
		Font fonteNegrito = new Font(Font.HELVETICA, 10, Font.BOLD);

		Document documento = new Document(PageSize.A4, 50, 50, 50, 50);
		try {
			ByteArrayOutputStream saida = new ByteArrayOutputStream();
			PdfWriter.getInstance(documento, saida);
			documento.open();

			documento.add(new Paragraph("Relatório do projeto '" + relatorio.getProjeto().getNome() + "'", fonteTitulo));
			documento.add(new Paragraph("Gerado em " + relatorio.getGeradoEm().format(FORMATO_DE_DATA_E_HORA)
					+ " por " + relatorio.getGeradoPor().getNome(), fonteSubtitulo));
			documento.add(Chunk.NEWLINE);

			for (String linha : relatorio.getConteudo().split("\n")) {
				documento.add(new Paragraph(linha, fonteCorpo));
			}

			if (relatorio.getTranscricaoAudio() != null && !relatorio.getTranscricaoAudio().isBlank()) {
				documento.add(Chunk.NEWLINE);
				documento.add(new Paragraph("OBSERVAÇÃO NARRADA:", fonteNegrito));
				documento.add(new Paragraph(relatorio.getTranscricaoAudio(), fonteCorpo));
			}

			documento.close();
			return saida.toByteArray();
		} catch (DocumentException excecao) {
			throw new RegraDeNegocioExcecao("Nao foi possivel gerar o PDF do relatorio.");
		}
	}

	// ---------- montagem do texto do relatorio (RF-04.2) ----------

	private String montarConteudo(Projeto projeto, List<Tarefa> tarefas, LocalDateTime inicioDoPeriodo) {
		List<Tarefa> concluidas = tarefas.stream()
				.filter(tarefa -> tarefa.getStatus() == StatusTarefa.CONCLUIDO).toList();
		List<Tarefa> concluidasNoPeriodo = concluidas.stream()
				.filter(tarefa -> tarefa.getConcluidaEm() != null
						&& tarefa.getConcluidaEm().isAfter(inicioDoPeriodo))
				.toList();
		List<Tarefa> emAberto = tarefas.stream()
				.filter(tarefa -> tarefa.getStatus() != StatusTarefa.CONCLUIDO).toList();
		List<Tarefa> bloqueadas = emAberto.stream()
				.filter(tarefa -> tarefa.getStatus() == StatusTarefa.BLOQUEADO).toList();
		List<Tarefa> proximosPrazos = emAberto.stream()
				.filter(tarefa -> tarefa.getPrazo() != null)
				.sorted(Comparator.comparing(Tarefa::getPrazo))
				.limit(5)
				.toList();

		int progresso = tarefas.isEmpty() ? 0 : (int) (concluidas.size() * 100L / tarefas.size());

		StringBuilder texto = new StringBuilder();
		texto.append("RELATORIO DO PROJETO '").append(projeto.getNome()).append("'\n");
		texto.append("Gerado em ").append(LocalDateTime.now().format(FORMATO_DE_DATA_E_HORA)).append("\n\n");

		texto.append("PROGRESSO GERAL: ").append(progresso).append("% (")
				.append(concluidas.size()).append(" de ").append(tarefas.size())
				.append(" tarefas concluidas)\n\n");

		texto.append("TAREFAS CONCLUIDAS NO PERIODO (desde ")
				.append(inicioDoPeriodo.format(FORMATO_DE_DATA)).append("):\n");
		if (concluidasNoPeriodo.isEmpty()) {
			texto.append("- Nenhuma.\n");
		} else {
			for (Tarefa tarefa : concluidasNoPeriodo) {
				texto.append("- ").append(descreverTarefa(tarefa)).append("\n");
			}
		}

		texto.append("\nTAREFAS EM ABERTO:\n");
		if (emAberto.isEmpty()) {
			texto.append("- Nenhuma.\n");
		} else {
			for (Tarefa tarefa : emAberto) {
				texto.append("- ").append(descreverTarefa(tarefa))
						.append(" [").append(rotuloDoStatus(tarefa.getStatus())).append("]\n");
			}
		}

		texto.append("\nIMPEDIMENTOS (TAREFAS BLOQUEADAS):\n");
		if (bloqueadas.isEmpty()) {
			texto.append("- Nenhum.\n");
		} else {
			for (Tarefa tarefa : bloqueadas) {
				texto.append("- ").append(descreverTarefa(tarefa)).append("\n");
			}
		}

		texto.append("\nPROXIMOS PRAZOS:\n");
		if (proximosPrazos.isEmpty()) {
			texto.append("- Nenhum prazo definido.\n");
		} else {
			for (Tarefa tarefa : proximosPrazos) {
				texto.append("- ").append(tarefa.getPrazo().format(FORMATO_DE_DATA))
						.append(" — ").append(tarefa.getTitulo()).append("\n");
			}
		}

		return texto.toString();
	}

	private String descreverTarefa(Tarefa tarefa) {
		String descricao = tarefa.getTitulo();
		if (!tarefa.getResponsaveis().isEmpty()) {
			String nomes = tarefa.getResponsaveis().stream()
					.map(Usuario::getNome)
					.collect(Collectors.joining(", "));
			descricao += " (responsaveis: " + nomes + ")";
		}
		return descricao;
	}

	private String rotuloDoStatus(StatusTarefa status) {
		return switch (status) {
			case A_FAZER -> "A Fazer";
			case EM_ANDAMENTO -> "Em Andamento";
			case BLOQUEADO -> "Bloqueado";
			case EM_REVISAO -> "Em Revisao";
			case CONCLUIDO -> "Concluido";
		};
	}

}
