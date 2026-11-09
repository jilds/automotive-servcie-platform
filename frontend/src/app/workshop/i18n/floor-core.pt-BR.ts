import { FloorCoreKey } from './floor-core.en';

/** Brazilian Portuguese workshop-floor texts shown outside the work order route, and the error codes. */
export const floorCorePtBR: Record<FloorCoreKey, string> = {
  'floor.banner.running': 'Cronômetro em andamento na ordem nº {number} {plate}',
  'floor.banner.open': 'Abrir a ordem nº {number}, o cronômetro está em andamento',
  'error.timer-not-running': 'Não há nenhum cronômetro em andamento nesta ordem.',
  'error.time-tracking-closed': 'Esta ordem está pronta ou encerrada, então não é possível iniciar o cronômetro.',
  'error.no-mechanic-assigned': 'Atribua a ordem a um mecânico antes de iniciar o cronômetro.',
  'error.time-entry-not-found': 'Registro de tempo não encontrado.',
  'error.invalid-time-range': 'Os horários não podem estar no futuro e o fim não pode ser anterior ao início.',
  'error.photo-not-found': 'Foto não encontrada.',
  'error.photo-too-large': 'Esta foto tem mais de 5 MB.',
  'error.photo-type-not-allowed': 'Só são aceitas imagens JPEG, PNG e WebP.',
  'error.photo-limit-reached': 'Esta ordem já tem 30 fotos. Apague uma para adicionar outra.',
  'error.checklist-incomplete': 'O veículo ainda não pode ser entregue: nem todos os itens obrigatórios do checklist de entrega foram respondidos.',
  'error.checklist-not-found': 'Este checklist não foi iniciado.',
  'error.checklist-item-not-found': 'Item do checklist não encontrado.',
  'error.photo-storage-full': 'A oficina esgotou o espaço para fotos. Apague fotos de que não precisa mais ou fale com o administrador da plataforma.',
  'error.time-entry-overlap': 'Este tempo se sobrepõe a outro registro do mesmo mecânico. Ajuste o início ou o fim.',
  'error.upload-too-large': 'Este arquivo excede o limite de envio de 5 MB.',
};
