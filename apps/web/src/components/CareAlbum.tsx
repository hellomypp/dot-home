import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";

type Photo = { id: string; name: string; image: string; canDelete: boolean };
type AlbumPage = { items: Photo[]; hasMore: boolean };

export function CareAlbum({ householdId }: { householdId: string }) {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Photo | null>(null);
  const client = useQueryClient();
  const root = `/api/households/${householdId}/care-album`;
  const key = ["care-album", householdId];
  const album = useQuery({ queryKey: [...key, page], queryFn: () => api<AlbumPage>(`${root}?page=${page}`) });
  const remove = useMutation({
    mutationFn: (photo: Photo) => api(`${root}/${photo.id}`, "DELETE"),
    onSuccess: async () => {
      setSelected(null);
      if (page > 0 && album.data?.items.length === 1) setPage(page - 1);
      await client.invalidateQueries({ queryKey: key });
    },
  });

  return (
    <section className="care-album" aria-label="돌봄 추억 사진">
      {album.isPending && <p role="status">앨범을 펼치는 중이에요…</p>}
      {album.isError && (
        <div><p role="alert">{album.error.message}</p>
          <button className="button secondary" onClick={() => void album.refetch()}>다시 열기</button>
        </div>
      )}
      {album.data && <>
        {album.data.items.length === 0 && <p className="empty-text">이 페이지에는 사진이 없어요.</p>}
        <ul className="care-album-grid">
          {album.data.items.map((photo) => (
            <li key={photo.id}>
              <figure>
                <img src={photo.image} alt="" width={96} height={96} />
                <figcaption>{photo.name}</figcaption>
              </figure>
              {photo.canDelete && <button className="button secondary" aria-label={`${photo.name} 사진 삭제`}
                onClick={() => { remove.reset(); setSelected(photo); }}>사진 삭제</button>}
              {selected?.id === photo.id && (
                <div className="care-album-confirm">
                  <p>{photo.name}의 사진을 앨범에서 지울까요?</p>
                  {remove.isError && <p role="alert">{remove.error.message}</p>}
                  <button className="button secondary" disabled={remove.isPending} onClick={() => setSelected(null)}>취소</button>
                  <button className="button primary" disabled={remove.isPending} onClick={() => remove.mutate(photo)}>사진 삭제 확인</button>
                </div>
              )}
            </li>
          ))}
        </ul>
        {(page > 0 || album.data.hasMore) && (
          <nav className="care-album-pages" aria-label="앨범 페이지">
            <button className="button secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
            <span>{page + 1}쪽</span>
            <button className="button secondary" disabled={!album.data.hasMore} onClick={() => setPage(page + 1)}>다음</button>
          </nav>
        )}
      </>}
    </section>
  );
}
