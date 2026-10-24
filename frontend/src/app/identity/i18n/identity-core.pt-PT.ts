import { IdentityCoreKey } from './identity-core.en';

/** Portuguese (Portugal) texts of the sign-in page and the identity error codes. */
export const identityCorePtPT: Record<IdentityCoreKey, string> = {
  'login.subtitleBranded': 'Entre em {name}',
  'login.poweredBy': 'com ASP',
  'login.contactTitle': 'Quer o ASP na sua oficina?',
  'login.contactBody': 'O ASP é vendido por subscrição. Contacte-nos e nós tratamos de tudo.',
  'error.workshop-suspended': 'A subscrição desta oficina está suspensa. Contacte a equipa ASP.',
  'error.workshop-not-found': 'Oficina não encontrada.',
  'error.activation-invalid': 'Esta ligação de ativação já não é válida. Peça uma nova.',
  'error.activation-expired': 'Esta ligação de ativação expirou. Peça uma nova.',
  'error.already-activated': 'Este utilizador já ativou a conta.',
  'error.slug-taken': 'Outra oficina já usa este endereço.',
  'error.logo-too-large': 'O logótipo pode ter no máximo 512 KB.',
  'error.logo-unsupported': 'O logótipo tem de ser uma imagem PNG, SVG ou WEBP.',
  'error.logo-unsafe': 'Este SVG tem scripts, ligações ou outro conteúdo ativo. Exporte-o como desenho simples ou use PNG.',
  'error.logo-not-found': 'Esta oficina não tem logótipo.',
};
