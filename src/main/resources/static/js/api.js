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

/* ---------- forca de senha (RNF-02) ----------
   Usado no cadastro (autenticacao.js) e na troca de senha do perfil
   (navegacao.js) — os dois lugares onde o usuario escolhe uma senha nova.
   A redefinicao por e-mail fica de fora de proposito (exige so 6 caracteres). */

const NIVEIS_DE_FORCA = [
	{ minimo: 0, rotulo: 'Muito fraca', classe: 'forca-muito-fraca' },
	{ minimo: 2, rotulo: 'Fraca', classe: 'forca-fraca' },
	{ minimo: 3, rotulo: 'Razoável', classe: 'forca-razoavel' },
	{ minimo: 4, rotulo: 'Boa', classe: 'forca-boa' },
	{ minimo: 5, rotulo: 'Forte', classe: 'forca-forte' }
];

/** Confere 5 criterios de senha forte e devolve a pontuacao e o que ainda falta. */
function avaliarForcaDaSenha(senha) {
	const criterios = [
		{ atende: senha.length >= 8, rotulo: 'pelo menos 8 caracteres' },
		{ atende: /[a-z]/.test(senha), rotulo: 'uma letra minúscula' },
		{ atende: /[A-Z]/.test(senha), rotulo: 'uma letra maiúscula' },
		{ atende: /[0-9]/.test(senha), rotulo: 'um número' },
		{ atende: /[^A-Za-z0-9]/.test(senha), rotulo: 'um caractere especial (ex.: ! @ # $)' }
	];
	return {
		pontuacao: criterios.filter(c => c.atende).length,
		comprimentoOk: criterios[0].atende,
		faltando: criterios.filter(c => !c.atende).map(c => c.rotulo)
	};
}

/** Senha minima aceita para cadastro/troca: pelo menos "Razoável" (RNF-02, mesma regra do backend). */
function senhaAtendeOMinimo(senha) {
	const { pontuacao, comprimentoOk } = avaliarForcaDaSenha(senha);
	return comprimentoOk && pontuacao >= 3;
}

/**
 * Liga um campo de senha a um medidor de forca visual (barra + texto do que
 * falta), atualizado a cada tecla digitada. idDoMedidor e o contêiner que
 * fica escondido enquanto o campo está vazio.
 */
function ligarMedidorDeForca(idDoCampo, idDoMedidor, idDaBarra, idDoTexto) {
	const campo = document.getElementById(idDoCampo);
	const medidor = document.getElementById(idDoMedidor);
	const barra = document.getElementById(idDaBarra);
	const texto = document.getElementById(idDoTexto);
	campo.addEventListener('input', () => {
		const senha = campo.value;
		medidor.hidden = senha.length === 0;
		if (senha.length === 0) {
			return;
		}
		const { pontuacao, faltando } = avaliarForcaDaSenha(senha);
		const nivel = [...NIVEIS_DE_FORCA].reverse().find(n => pontuacao >= n.minimo);
		barra.style.width = (pontuacao / 5 * 100) + '%';
		barra.className = nivel.classe;
		texto.textContent = faltando.length === 0
			? 'Força: ' + nivel.rotulo + ' — sua senha está ótima!'
			: 'Força: ' + nivel.rotulo + ' — falta incluir ' + faltando.join(', ') + '.';
	});
}
