/* ============================================================
   autenticacao.js — tela de login e cadastro (RF-01.1)
   ============================================================ */

// se ja existe uma sessao valida, vai direto para a lista de projetos
if (localStorage.getItem('kanvox_token') && usuarioLogado()) {
	window.location.href = '/projetos.html';
}

const abas = document.querySelector('.abas');
const abaEntrar = document.getElementById('aba-entrar');
const abaCadastrar = document.getElementById('aba-cadastrar');
const formularioEntrar = document.getElementById('formulario-entrar');
const formularioCadastrar = document.getElementById('formulario-cadastrar');
const painelEsqueciSenha = document.getElementById('painel-esqueci-senha');
const formularioEsqueciSenha = document.getElementById('formulario-esqueci-senha');
const mensagem = document.getElementById('mensagem');

function mostrarAba(qual) {
	const entrando = qual === 'entrar';
	abaEntrar.classList.toggle('ativa', entrando);
	abaCadastrar.classList.toggle('ativa', !entrando);
	// aria-selected acompanha a aba visualmente ativa (RNF-05: acessibilidade)
	abaEntrar.setAttribute('aria-selected', String(entrando));
	abaCadastrar.setAttribute('aria-selected', String(!entrando));
	formularioEntrar.hidden = !entrando;
	formularioCadastrar.hidden = entrando;
	mensagem.hidden = true;
}

abaEntrar.addEventListener('click', () => mostrarAba('entrar'));
abaCadastrar.addEventListener('click', () => mostrarAba('cadastrar'));

/* ---------- recuperacao de senha (RF-01.3) ---------- */

document.getElementById('link-esqueci-senha').addEventListener('click', (evento) => {
	evento.preventDefault();
	abas.hidden = true;
	formularioEntrar.hidden = true;
	formularioCadastrar.hidden = true;
	painelEsqueciSenha.hidden = false;
	mensagem.hidden = true;
});

document.getElementById('link-voltar-login').addEventListener('click', (evento) => {
	evento.preventDefault();
	painelEsqueciSenha.hidden = true;
	abas.hidden = false;
	mostrarAba('entrar');
});

formularioEsqueciSenha.addEventListener('submit', async (evento) => {
	evento.preventDefault();
	try {
		const resposta = await chamarApi('/autenticacao/esqueci-senha', {
			method: 'POST',
			body: { email: document.getElementById('esqueci-email').value }
		});
		formularioEsqueciSenha.reset();
		painelEsqueciSenha.hidden = true;
		abas.hidden = false;
		mostrarAba('entrar');
		exibirMensagem(resposta.mensagem, 'sucesso');
	} catch (erro) {
		mostrarErro(erro.message);
	}
});

function mostrarErro(texto) {
	mensagem.textContent = texto;
	mensagem.hidden = false;
}

/** Depois do login, busca os dados do usuario e vai para a lista de projetos. */
async function concluirLogin(token) {
	localStorage.setItem('kanvox_token', token);
	const usuario = await chamarApi('/autenticacao/eu');
	localStorage.setItem('kanvox_usuario', JSON.stringify(usuario));
	window.location.href = '/projetos.html';
}

formularioEntrar.addEventListener('submit', async (evento) => {
	evento.preventDefault();
	try {
		const resposta = await chamarApi('/autenticacao/login', {
			method: 'POST',
			body: {
				email: document.getElementById('entrar-email').value,
				senha: document.getElementById('entrar-senha').value
			}
		});
		await concluirLogin(resposta.token);
	} catch (erro) {
		mostrarErro(erro.message);
	}
});

/* ---------- forca da senha no cadastro (RF-01.1 / RNF-02) ---------- */
/* avaliarForcaDaSenha, senhaAtendeOMinimo e ligarMedidorDeForca vem de
   api.js — a mesma barra e reaproveitada na troca de senha do perfil. */

const campoSenhaCadastro = document.getElementById('cadastrar-senha');
ligarMedidorDeForca('cadastrar-senha', 'medidor-de-senha', 'barra-de-forca-preenchimento', 'texto-forca-da-senha');

formularioCadastrar.addEventListener('submit', async (evento) => {
	evento.preventDefault();
	if (!senhaAtendeOMinimo(campoSenhaCadastro.value)) {
		mostrarErro('Escolha uma senha mais forte: combine letras maiúsculas, minúsculas, números ou símbolos, com pelo menos 8 caracteres.');
		return;
	}
	try {
		const email = document.getElementById('cadastrar-email').value;
		const senha = document.getElementById('cadastrar-senha').value;
		await chamarApi('/autenticacao/cadastro', {
			method: 'POST',
			body: {
				nome: document.getElementById('cadastrar-nome').value,
				email: email,
				senha: senha
			}
		});
		// conta criada: faz o login automaticamente
		const resposta = await chamarApi('/autenticacao/login', {
			method: 'POST',
			body: { email: email, senha: senha }
		});
		await concluirLogin(resposta.token);
	} catch (erro) {
		mostrarErro(erro.message);
	}
});
