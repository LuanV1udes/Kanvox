/* ============================================================
   api.js — comunicacao com o backend do Kanvox
   Toda chamada HTTP do frontend passa por aqui: o token JWT e
   anexado automaticamente e os erros viram mensagens amigaveis.
   ============================================================ */

/**
 * Chama a API do backend. Exemplos:
 *   chamarApi('/projetos')                                  -> GET
 *   chamarApi('/projetos', { method: 'POST', body: {...} }) -> POST com JSON
 *   chamarApi('/projetos/1/transcricoes', { method: 'POST', body: formData })
 */
async function chamarApi(rota, opcoes = {}) {
	const cabecalhos = {};
	const token = localStorage.getItem('kanvox_token');
	if (token) {
		cabecalhos['Authorization'] = 'Bearer ' + token;
	}
	// corpo JSON: objetos comuns sao serializados; FormData (audio) vai como esta
	if (opcoes.body && !(opcoes.body instanceof FormData)) {
		cabecalhos['Content-Type'] = 'application/json';
		opcoes.body = JSON.stringify(opcoes.body);
	}

	const resposta = await fetch('/api' + rota, { ...opcoes, headers: cabecalhos });

	// token expirado ou invalido: volta para a tela de login (RNF-02)
	if (resposta.status === 401) {
		localStorage.removeItem('kanvox_token');
		localStorage.removeItem('kanvox_usuario');
		window.location.href = '/index.html';
		throw new Error('Sessao expirada. Entre novamente.');
	}

	const corpo = await resposta.json().catch(() => null);
	if (!resposta.ok) {
		throw new Error(corpo && corpo.erro ? corpo.erro : 'Erro inesperado ao falar com o servidor.');
	}
	return corpo;
}

/** Usuario logado (id, nome, email), guardado no login. */
function usuarioLogado() {
	const dados = localStorage.getItem('kanvox_usuario');
	return dados ? JSON.parse(dados) : null;
}

/** Sai da conta: apaga o token e volta para o login. */
function sairDaConta() {
	localStorage.removeItem('kanvox_token');
	localStorage.removeItem('kanvox_usuario');
	window.location.href = '/index.html';
}

/** Redireciona para o login se nao houver sessao (usado nas paginas internas). */
function exigirSessao() {
	if (!localStorage.getItem('kanvox_token') || !usuarioLogado()) {
		window.location.href = '/index.html';
	}
}

/** Mostra uma mensagem flutuante no rodape (tipo: 'sucesso' | 'erro' | ''). */
function exibirMensagem(texto, tipo = '') {
	const anterior = document.querySelector('.mensagem-flutuante');
	if (anterior) {
		anterior.remove();
	}
	const elemento = document.createElement('div');
	elemento.className = 'mensagem-flutuante ' + tipo;
	elemento.textContent = texto;
	document.body.appendChild(elemento);
	setTimeout(() => elemento.remove(), 4000);
}

/** Converte "2026-08-15" em "15/08/2026". */
function formatarData(dataIso) {
	if (!dataIso) {
		return '';
	}
	const [ano, mes, dia] = dataIso.split('T')[0].split('-');
	return dia + '/' + mes + '/' + ano;
}

/** Converte "2026-08-15T14:30:00" em "15/08/2026 14:30". */
function formatarDataEHora(dataIso) {
	if (!dataIso) {
		return '';
	}
	const [data, hora] = dataIso.split('T');
	return formatarData(data) + ' ' + hora.slice(0, 5);
}

/** Evita que textos digitados pelos usuarios sejam interpretados como HTML. */
function escaparHtml(texto) {
	const div = document.createElement('div');
	div.textContent = texto == null ? '' : String(texto);
	return div.innerHTML;
}
