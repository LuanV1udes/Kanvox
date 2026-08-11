/* ============================================================
   redefinir-senha.js — pagina de redefinicao de senha (RF-01.3)
   O usuario chega aqui pelo link enviado por e-mail, com o
   token de recuperacao na propria URL (?token=...).
   ============================================================ */

const token = new URLSearchParams(window.location.search).get('token');
const formulario = document.getElementById('formulario-redefinir-senha');
const mensagem = document.getElementById('mensagem');
const emailDaConta = document.getElementById('email-da-conta');

function mostrarErro(texto) {
	mensagem.textContent = texto;
	mensagem.hidden = false;
}

/** Confere o token e mostra de qual conta a senha esta sendo redefinida, antes do usuario digitar a nova senha. */
async function identificarConta() {
	if (!token) {
		formulario.hidden = true;
		mostrarErro('Link de redefinição inválido. Solicite um novo na tela de login.');
		return;
	}
	try {
		const resposta = await chamarApi('/autenticacao/token-recuperacao/' + encodeURIComponent(token));
		emailDaConta.textContent = 'Redefinindo a senha de ' + resposta.email;
		emailDaConta.hidden = false;
	} catch (erro) {
		formulario.hidden = true;
		mostrarErro(erro.message);
	}
}

identificarConta();

formulario.addEventListener('submit', async (evento) => {
	evento.preventDefault();
	const novaSenha = document.getElementById('nova-senha').value;
	const confirmarSenha = document.getElementById('confirmar-senha').value;
	if (novaSenha !== confirmarSenha) {
		mostrarErro('As senhas não coincidem.');
		return;
	}
	try {
		const resposta = await chamarApi('/autenticacao/redefinir-senha', {
			method: 'POST',
			body: { token: token, novaSenha: novaSenha }
		});
		formulario.hidden = true;
		mensagem.hidden = true;
		exibirMensagem(resposta.mensagem, 'sucesso');
		setTimeout(() => { window.location.href = '/index.html'; }, 2500);
	} catch (erro) {
		mostrarErro(erro.message);
	}
});
