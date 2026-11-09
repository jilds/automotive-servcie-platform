import type { MessageKey } from '../../i18n';
import type { PageId } from '../../i18n/routes';

export type PostId = Extract<PageId, 'post1' | 'post2' | 'post3'>;

interface PostKeys {
  title: MessageKey;
  summary: MessageKey;
  intro: MessageKey;
  sections: readonly { heading: MessageKey; text: MessageKey }[];
  closing: MessageKey;
}

/** The blog posts, newest first (dates are ISO days). Written out key by key so a missing text fails `astro check`. */
export const posts: readonly { id: PostId; published: string }[] = [
  { id: 'post3', published: '2026-11-04' },
  { id: 'post2', published: '2026-10-28' },
  { id: 'post1', published: '2026-10-21' },
];

const keys: Record<PostId, PostKeys> = {
  post1: {
    title: 'blog.post1.title',
    summary: 'blog.post1.summary',
    intro: 'blog.post1.p0',
    sections: [
      { heading: 'blog.post1.h1', text: 'blog.post1.p1' },
      { heading: 'blog.post1.h2', text: 'blog.post1.p2' },
      { heading: 'blog.post1.h3', text: 'blog.post1.p3' },
    ],
    closing: 'blog.post1.p4',
  },
  post2: {
    title: 'blog.post2.title',
    summary: 'blog.post2.summary',
    intro: 'blog.post2.p0',
    sections: [
      { heading: 'blog.post2.h1', text: 'blog.post2.p1' },
      { heading: 'blog.post2.h2', text: 'blog.post2.p2' },
      { heading: 'blog.post2.h3', text: 'blog.post2.p3' },
    ],
    closing: 'blog.post2.p4',
  },
  post3: {
    title: 'blog.post3.title',
    summary: 'blog.post3.summary',
    intro: 'blog.post3.p0',
    sections: [
      { heading: 'blog.post3.h1', text: 'blog.post3.p1' },
      { heading: 'blog.post3.h2', text: 'blog.post3.p2' },
      { heading: 'blog.post3.h3', text: 'blog.post3.p3' },
    ],
    closing: 'blog.post3.p4',
  },
};

/** The dictionary keys of one post. */
export function postKeys(id: PostId): PostKeys {
  return keys[id];
}
