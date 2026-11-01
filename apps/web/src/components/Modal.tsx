import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

export function Modal({title, children, onClose}: {title:string;children:ReactNode;onClose:()=>void}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { const node = dialog.current!; node.showModal(); return () => node.close(); }, []);
  return <dialog ref={dialog} className="modal" onCancel={onClose} onClick={e => {if (e.target === e.currentTarget) onClose();}}>
    <div className="modal-head"><h2>{title}</h2><button className="icon-button" aria-label="닫기" onClick={onClose}><X size={20}/></button></div>
    {children}
  </dialog>;
}
