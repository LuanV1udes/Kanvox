package br.edu.fatec.kanvox.servico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/** Envio de e-mails da plataforma: boas-vindas no cadastro (RF-01.1) e recuperacao de senha (RF-01.3). */
@Service
public class EmailServico {

	private static final Logger registrador = LoggerFactory.getLogger(EmailServico.class);

	// Mesmas cores da identidade visual do frontend (src/main/resources/static/css/estilo.css)
	private static final String COR_TINTA = "#14161a";
	private static final String COR_TINTA_CONTRASTE = "#ffffff";
	private static final String COR_TINTA_MEDIA = "#4b5261";
	private static final String COR_TINTA_SUAVE = "#79808f";
	private static final String COR_ACENTO = "#2f5cd6";
	private static final String COR_FUNDO = "#fbfbfc";
	private static final String COR_SUPERFICIE = "#ffffff";
	private static final String COR_BORDA = "#e7e8ec";

	private final JavaMailSender mailSender;
	private final String remetente;
	private final String urlBase;

	public EmailServico(JavaMailSender mailSender,
			@Value("${spring.mail.username:}") String remetente,
			@Value("${kanvox.url-base}") String urlBase) {
		this.mailSender = mailSender;
		this.remetente = remetente;
		this.urlBase = urlBase;
	}

	/**
	 * Confirma a criacao da conta (RF-01.1) com uma mensagem de boas-vindas em
	 * HTML, na mesma identidade visual do Kanvox, com versao em texto puro como
	 * alternativa. Assim como o e-mail de recuperacao, falhas de envio sao
	 * apenas registradas em log (RNF-03) — a conta ja foi criada com sucesso
	 * independente do e-mail chegar ou nao.
	 */
	public void enviarBoasVindas(String destinatario, String nomeDoUsuario) {
		String link = urlBase + "/index.html";

		try {
			MimeMessage mensagem = mailSender.createMimeMessage();
			MimeMessageHelper ajudante = new MimeMessageHelper(mensagem, true, "UTF-8");
			if (!remetente.isBlank()) {
				ajudante.setFrom(remetente);
			}
			ajudante.setTo(destinatario);
			ajudante.setSubject("Bem-vindo(a) ao Kanvox!");
			ajudante.setText(montarTextoPuroBoasVindas(nomeDoUsuario, link), montarHtmlBoasVindas(nomeDoUsuario, link));
			mailSender.send(mensagem);
		} catch (Exception e) {
			registrador.error("Falha ao enviar e-mail de boas-vindas para {}", destinatario, e);
		}
	}

	private String montarTextoPuroBoasVindas(String nomeDoUsuario, String link) {
		return "Olá, " + nomeDoUsuario + "!\n\n"
				+ "Sua conta no Kanvox foi criada com sucesso. Agora você já pode entrar na plataforma, "
				+ "criar projetos e organizar as tarefas da sua equipe.\n\n"
				+ "Acesse: " + link + "\n\n"
				+ "Se você não fez esse cadastro, ignore este e-mail.";
	}

	/** Mesmo layout de tabelas do e-mail de recuperacao de senha, trocando o conteudo pelo texto de boas-vindas. */
	private String montarHtmlBoasVindas(String nomeDoUsuario, String link) {
		return "<!DOCTYPE html><html lang=\"pt-BR\"><body style=\"margin:0;padding:32px 16px;"
				+ "background:" + COR_FUNDO + ";font-family:-apple-system,'Segoe UI',Roboto,Arial,sans-serif;\">"
				+ "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">"
				+ "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" "
				+ "style=\"max-width:480px;width:100%;background:" + COR_SUPERFICIE + ";border:1px solid " + COR_BORDA + ";"
				+ "border-radius:14px;padding:36px 32px;\">"

				+ "<tr><td align=\"center\" style=\"padding-bottom:22px;\">"
				+ "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
				+ "<td style=\"width:30px;height:30px;background:" + COR_TINTA + ";border-radius:8px;"
				+ "color:" + COR_TINTA_CONTRASTE + ";font-size:15px;font-weight:700;text-align:center;"
				+ "vertical-align:middle;font-family:Arial,sans-serif;\">K</td>"
				+ "<td style=\"padding-left:9px;font-size:22px;font-weight:700;color:" + COR_TINTA + ";"
				+ "letter-spacing:-0.03em;\">Kanvox</td>"
				+ "</tr></table>"
				+ "</td></tr>"

				+ "<tr><td style=\"font-size:15px;line-height:1.6;color:" + COR_TINTA + ";\">"
				+ "<p style=\"margin:0 0 14px;\">Olá, <strong>" + escaparHtml(nomeDoUsuario) + "</strong>!</p>"
				+ "<p style=\"margin:0 0 24px;color:" + COR_TINTA_MEDIA + ";\">Sua conta no Kanvox foi criada com "
				+ "sucesso! Agora você já pode entrar na plataforma, criar projetos e organizar as tarefas da "
				+ "sua equipe.</p>"
				+ "</td></tr>"

				+ "<tr><td align=\"center\" style=\"padding-bottom:24px;\">"
				+ "<a href=\"" + link + "\" style=\"display:inline-block;background:" + COR_ACENTO + ";"
				+ "color:" + COR_TINTA_CONTRASTE + ";text-decoration:none;font-size:14px;font-weight:600;"
				+ "padding:12px 28px;border-radius:8px;font-family:Arial,sans-serif;\">Acessar o Kanvox</a>"
				+ "</td></tr>"

				+ "<tr><td style=\"font-size:13px;color:" + COR_TINTA_SUAVE + ";line-height:1.6;"
				+ "border-top:1px solid " + COR_BORDA + ";padding-top:18px;\">"
				+ "Se o botão não funcionar, copie e cole este link no navegador:<br>"
				+ "<a href=\"" + link + "\" style=\"color:" + COR_ACENTO + ";word-break:break-all;\">" + link + "</a>"
				+ "<p style=\"margin:16px 0 0;\">Se você não fez esse cadastro, pode ignorar este e-mail.</p>"
				+ "</td></tr>"

				+ "</table>"
				+ "</td></tr></table>"
				+ "</body></html>";
	}

	/**
	 * Envia o link de redefinicao de senha (RF-01.3) em HTML, com a identidade
	 * visual do Kanvox, acompanhado de uma versao em texto puro como alternativa
	 * (para clientes de e-mail que nao renderizam HTML).
	 * Falhas de envio sao apenas registradas em log (RNF-03) — o chamador nunca
	 * deve revelar ao usuario se o envio deu certo, para nao expor quais
	 * e-mails estao cadastrados na plataforma.
	 */
	public void enviarLinkDeRecuperacao(String destinatario, String nomeDoUsuario, String token) {
		String link = urlBase + "/redefinir-senha.html?token=" + token;

		try {
			MimeMessage mensagem = mailSender.createMimeMessage();
			// multipart=true e obrigatorio para setText(textoPuro, html) funcionar (alternativas de conteudo)
			MimeMessageHelper ajudante = new MimeMessageHelper(mensagem, true, "UTF-8");
			if (!remetente.isBlank()) {
				ajudante.setFrom(remetente);
			}
			ajudante.setTo(destinatario);
			ajudante.setSubject("Kanvox — Redefinição de senha");
			ajudante.setText(montarTextoPuro(nomeDoUsuario, link), montarHtml(nomeDoUsuario, link));
			mailSender.send(mensagem);
		} catch (Exception e) {
			// captura tambem excecoes que nao sao MailException/MessagingException (ex.
			// erro de configuracao do MimeMessageHelper) — envio de e-mail e um
			// integracao externa opcional e nunca pode derrubar o resto do sistema (RNF-03)
			registrador.error("Falha ao enviar e-mail de recuperacao de senha para {}", destinatario, e);
		}
	}

	private String montarTextoPuro(String nomeDoUsuario, String link) {
		return "Olá, " + nomeDoUsuario + "!\n\n"
				+ "Recebemos uma solicitação para redefinir sua senha no Kanvox.\n"
				+ "Acesse o link abaixo para criar uma nova senha (válido por 1 hora):\n\n"
				+ link + "\n\n"
				+ "Se você não fez essa solicitação, pode ignorar este e-mail — sua senha atual continua válida.";
	}

	/** Layout simples baseado em tabelas, para renderizar de forma consistente nos principais clientes de e-mail. */
	private String montarHtml(String nomeDoUsuario, String link) {
		return "<!DOCTYPE html><html lang=\"pt-BR\"><body style=\"margin:0;padding:32px 16px;"
				+ "background:" + COR_FUNDO + ";font-family:-apple-system,'Segoe UI',Roboto,Arial,sans-serif;\">"
				+ "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">"
				+ "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" "
				+ "style=\"max-width:480px;width:100%;background:" + COR_SUPERFICIE + ";border:1px solid " + COR_BORDA + ";"
				+ "border-radius:14px;padding:36px 32px;\">"

				+ "<tr><td align=\"center\" style=\"padding-bottom:22px;\">"
				+ "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
				+ "<td style=\"width:30px;height:30px;background:" + COR_TINTA + ";border-radius:8px;"
				+ "color:" + COR_TINTA_CONTRASTE + ";font-size:15px;font-weight:700;text-align:center;"
				+ "vertical-align:middle;font-family:Arial,sans-serif;\">K</td>"
				+ "<td style=\"padding-left:9px;font-size:22px;font-weight:700;color:" + COR_TINTA + ";"
				+ "letter-spacing:-0.03em;\">Kanvox</td>"
				+ "</tr></table>"
				+ "</td></tr>"

				+ "<tr><td style=\"font-size:15px;line-height:1.6;color:" + COR_TINTA + ";\">"
				+ "<p style=\"margin:0 0 14px;\">Olá, <strong>" + escaparHtml(nomeDoUsuario) + "</strong>!</p>"
				+ "<p style=\"margin:0 0 24px;color:" + COR_TINTA_MEDIA + ";\">Recebemos uma solicitação para "
				+ "redefinir sua senha no Kanvox. Clique no botão abaixo para criar uma nova senha — o link é "
				+ "válido por <strong>1 hora</strong>.</p>"
				+ "</td></tr>"

				+ "<tr><td align=\"center\" style=\"padding-bottom:24px;\">"
				+ "<a href=\"" + link + "\" style=\"display:inline-block;background:" + COR_ACENTO + ";"
				+ "color:" + COR_TINTA_CONTRASTE + ";text-decoration:none;font-size:14px;font-weight:600;"
				+ "padding:12px 28px;border-radius:8px;font-family:Arial,sans-serif;\">Redefinir minha senha</a>"
				+ "</td></tr>"

				+ "<tr><td style=\"font-size:13px;color:" + COR_TINTA_SUAVE + ";line-height:1.6;"
				+ "border-top:1px solid " + COR_BORDA + ";padding-top:18px;\">"
				+ "Se o botão não funcionar, copie e cole este link no navegador:<br>"
				+ "<a href=\"" + link + "\" style=\"color:" + COR_ACENTO + ";word-break:break-all;\">" + link + "</a>"
				+ "<p style=\"margin:16px 0 0;\">Se você não fez essa solicitação, pode ignorar este e-mail — "
				+ "sua senha atual continua válida.</p>"
				+ "</td></tr>"

				+ "</table>"
				+ "</td></tr></table>"
				+ "</body></html>";
	}

	/** O nome do usuario e digitado por ele mesmo no cadastro — nunca inserir sem escapar num corpo HTML. */
	private String escaparHtml(String texto) {
		return texto == null ? "" : texto
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}

}
