package br.edu.fatec.kanvox;

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
		SpringApplication.run(KanvoxAplicacao.class, args);
	}

}
