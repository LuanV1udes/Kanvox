package br.edu.fatec.kanvox.servico.transcricao;

/**
 * Abstracao do servico de transcricao de audio (RF-05.2).
 * Isola o provedor de IA do resto do sistema (RNF-04): trocar de provedor
 * no futuro significa criar outra implementacao desta interface, sem
 * alterar controladores ou outros servicos. A chamada ao Whisper sempre
 * passa por aqui — nunca e feita diretamente de um controlador.
 */
public interface ServicoTranscricao {

	/**
	 * Recebe o audio gravado e devolve o texto transcrito.
	 * O nome do arquivo e necessario para o provedor identificar o formato
	 * do audio (ex. gravacao.webm, narracao.wav).
	 */
	String transcrever(byte[] audio, String nomeDoArquivo);

}
