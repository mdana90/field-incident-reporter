'use client';

import { useState } from 'react';

type GalleryPhoto = { id: string; src: string; previewable: boolean };

export function PhotoGallery({ photos }: { photos: GalleryPhoto[] }) {
  const [selected, setSelected] = useState(0);
  // Photos can be removed elsewhere while this state is kept; never point past the end.
  const index = Math.min(selected, photos.length - 1);
  const current = photos[index];
  if (!current) return null;

  return (
    <div>
      <div className="relative h-[216px] overflow-hidden bg-[#dfe7e5] md:h-[320px]">
        {current.previewable ? (
          <img src={current.src} alt={`Incident photo ${index + 1}`} className="size-full object-contain" />
        ) : (
          <div className="grid size-full place-items-center text-center text-xs text-[#5e7378]">
            <p>
              Preview not available
              <br />
              <a href={current.src} target="_blank" rel="noreferrer" className="mt-2 inline-block font-extrabold text-teal">Open file</a>
            </p>
          </div>
        )}
        <span className="absolute bottom-3.5 right-4 rounded-md bg-[#172e35c9] px-2.5 py-1.5 text-[10px] font-bold text-white">
          {`${index + 1} / ${photos.length} ${photos.length === 1 ? 'photo' : 'photos'}`}
        </span>
      </div>
      {photos.length > 1 && (
        <div className="flex gap-2 border-b border-line px-[22px] py-3">
          {photos.map((photo, position) => (
            <button
              key={photo.id}
              type="button"
              onClick={() => setSelected(position)}
              aria-label={`Show photo ${position + 1}`}
              aria-current={position === index}
              className={`size-12 overflow-hidden rounded-lg border-2 bg-[#dfe7e5] ${position === index ? 'border-teal' : 'border-transparent'}`}
            >
              {photo.previewable && <img src={photo.src} alt="" className="size-full object-cover" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
