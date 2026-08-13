package br.edu.fatec.kanvox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principal do Kanvox — sistema web de gerenciamento de projetos
 * com transcricao de audio para relatorios (TG Fatec Ourinhos).
 * EnableScheduling ativa as rotinas agendadas (@Scheduled), usadas pela
 * verificacao periodica de tarefas com prazo vencido (RF-06.2).
 */
@SpringBootApplication
@EnableScheduling
public class KanvoxAplicacao {

	public static void main(String[] args) {
		carregarArquivoEnv();
		SpringApplication.run(KanvoxAplicacao.class, args);
	}

	/**
	 * Le o arquivo .env da raiz do projeto (se existir) e expoe cada linha
	 * CHAVE=valor como propriedade do sistema, antes do Spring resolver os
	 * ${...} do application.properties (RNF-02: credenciais fora do codigo).
	 * Precisa ser feito aqui, na mao, porque a biblioteca spring-dotenv que o
	 * projeto usava nao e compativel com esta versao do Spring Boot (ela se
	 * registra pelo mecanismo antigo META-INF/spring.factories, que deixou de
	 * ser lido — e falha calada, sem nenhum erro no log).
	 */
	private static void carregarArquivoEnv() {
		Path arquivo = Path.of(".env");
		if (!Files.isReadable(arquivo)) {
			return;
		}
		try {
			String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
			if (conteudo.startsWith("﻿")) {
				conteudo = conteudo.substring(1); // BOM que alguns editores gravam no inicio do arquivo
			}
			for (String linha : conteudo.lines().toList()) {
				String semEspacos = linha.strip();
				if (semEspacos.isEmpty() || semEspacos.startsWith("#")) {
					continue;
				}
				int separador = semEspacos.indexOf('=');
				if (separador < 0) {
					continue;
				}
				String chave = semEspacos.substring(0, separador).strip();
				String valor = semEspacos.substring(separador + 1).strip();
				// nunca sobrescreve uma variavel ja definida na maquina (ex.: -D na linha de comando)
				if (System.getProperty(chave) == null && System.getenv(chave) == null) {
					System.setProperty(chave, valor);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Falha ao ler o arquivo .env", e);
		}
	}

}
