import { IdentityCoreKey } from './identity-core.en';

/** Portuguese (Brazil) texts of the sign-in page and the identity error codes. */
export const identityCorePtBR: Record<IdentityCoreKey, string> = {
  'login.subtitleBranded': 'Entre em {name}',
  'login.poweredBy': 'com ASP',
  'login.contactTitle': 'Quer o ASP na sua oficina?',
  'login.contactBody': 'O ASP é vendido por assinatura. Fale com a gente e nós configuramos tudo.',
  'error.workshop-suspended': 'A assinatura desta oficina está suspensa. Fale com a equipe ASP.',
  'error.workshop-not-found': 'Oficina não encontrada.',
  'error.add-on-not-available': 'Este serviço não está disponível nesta oficina.',
  'error.too-many-requests': 'Muitas solicitações. Aguarde um momento e tente novamente.',
  'error.privacy-consent-required': 'Aceite a política de privacidade para enviar este formulário.',
  'error.activation-invalid': 'Este link de ativação não é mais válido. Peça um novo.',
  'error.activation-expired': 'Este link de ativação expirou. Peça um novo.',
  'error.already-activated': 'Este usuário já ativou a conta.',
  'error.slug-taken': 'Outra oficina já usa este endereço.',
  'error.logo-too-large': 'O logotipo pode ter no máximo 512 KB.',
  'error.logo-unsupported': 'O logotipo precisa ser uma imagem PNG, SVG ou WEBP.',
  'error.logo-unsafe': 'Este SVG tem scripts, links ou outro conteúdo ativo. Exporte como desenho simples ou use PNG.',
  'error.logo-not-found': 'Esta oficina não tem logotipo.',
};
