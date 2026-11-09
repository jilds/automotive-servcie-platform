import { FloorCoreKey } from './floor-core.en';

/** Portuguese (Portugal) workshop-floor texts shown outside the work order route, and the error codes. */
export const floorCorePtPT: Record<FloorCoreKey, string> = {
  'floor.banner.running': 'Cronómetro a decorrer na ordem n.º {number} {plate}',
  'floor.banner.open': 'Abrir a ordem n.º {number}, o cronómetro está a decorrer',
  'error.timer-not-running': 'Não há nenhum cronómetro a decorrer nesta ordem.',
  'error.time-tracking-closed': 'Esta ordem está pronta ou fechada, por isso não é possível iniciar o cronómetro.',
  'error.no-mechanic-assigned': 'Atribua a ordem a um mecânico antes de iniciar o cronómetro.',
  'error.time-entry-not-found': 'Registo de tempo não encontrado.',
  'error.invalid-time-range': 'As horas não podem estar no futuro e o fim não pode ser anterior ao início.',
  'error.photo-not-found': 'Fotografia não encontrada.',
  'error.photo-too-large': 'Esta fotografia tem mais de 5 MB.',
  'error.photo-type-not-allowed': 'Só são aceites imagens JPEG, PNG e WebP.',
  'error.photo-limit-reached': 'Esta ordem já tem 30 fotografias. Apague uma para acrescentar outra.',
  'error.checklist-incomplete': 'A viatura ainda não pode ser entregue: nem todos os itens obrigatórios da lista de entrega estão respondidos.',
  'error.checklist-not-found': 'Esta lista de verificação não foi iniciada.',
  'error.checklist-item-not-found': 'Item da lista de verificação não encontrado.',
  'error.photo-storage-full': 'A oficina esgotou o espaço para fotografias. Apague fotografias de que já não precisa ou fale com o administrador da plataforma.',
  'error.time-entry-overlap': 'Este tempo sobrepõe-se a outro registo do mesmo mecânico. Ajuste o início ou o fim.',
  'error.upload-too-large': 'Este ficheiro excede o limite de envio de 5 MB.',
};
