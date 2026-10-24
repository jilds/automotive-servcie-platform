import { CatalogueCoreKey } from './catalogue-core.en';

/** Portuguese (Brazil) error codes of the catalogue. */
export const catalogueCorePtBR: Record<CatalogueCoreKey, string> = {
  'error.service-not-found': 'Esse serviço não está no catálogo.',
  'error.package-not-found': 'Esse pacote não está no catálogo.',
  'error.service-inactive': 'Esse serviço não é mais oferecido.',
  'error.package-inactive': 'Esse pacote não é mais oferecido.',
  'error.package-empty': 'Um pacote precisa de pelo menos um serviço.',
};
