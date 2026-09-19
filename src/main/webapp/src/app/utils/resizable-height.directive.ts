import { AfterViewInit, Directive, ElementRef, Input, OnDestroy, Renderer2 } from '@angular/core';

/** Makes the host element's height user-resizable by dragging a handle bar appended to its bottom
 * border. Intended for wrapping a fixed-height child (e.g. an ag-grid) whose own height is set to
 * 100% of this host. */
@Directive({
  selector: '[appResizableHeight]',
  standalone: true,
})
export class ResizableHeightDirective implements AfterViewInit, OnDestroy {

  @Input() minHeight = 150;

  private handle?: HTMLElement;
  private startY = 0;
  private startHeight = 0;
  private readonly onMouseMove = (event: MouseEvent) => this.resize(event);
  private readonly onMouseUp = () => this.stopResize();

  constructor(private readonly el: ElementRef<HTMLElement>, private readonly renderer: Renderer2) {
  }

  ngAfterViewInit() {
    const host = this.el.nativeElement;
    this.renderer.setStyle(host, 'position', 'relative');

    const handle = this.renderer.createElement('div') as HTMLElement;
    this.renderer.setStyle(handle, 'position', 'absolute');
    this.renderer.setStyle(handle, 'left', '0');
    this.renderer.setStyle(handle, 'right', '0');
    this.renderer.setStyle(handle, 'bottom', '-4px');
    this.renderer.setStyle(handle, 'height', '7px');
    this.renderer.setStyle(handle, 'cursor', 'ns-resize');
    this.renderer.setStyle(handle, 'z-index', '10');
    this.renderer.setStyle(handle, 'border-bottom', '3px solid #ced4da');
    this.renderer.listen(handle, 'mouseenter', () => this.renderer.setStyle(handle, 'border-bottom-color', '#0d6efd'));
    this.renderer.listen(handle, 'mouseleave', () => {
      if (!this.startY) {
        this.renderer.setStyle(handle, 'border-bottom-color', '#ced4da');
      }
    });
    this.renderer.listen(handle, 'mousedown', (event: MouseEvent) => this.startResize(event));
    this.renderer.appendChild(host, handle);
    this.handle = handle;
  }

  ngOnDestroy() {
    this.stopResize();
  }

  private startResize(event: MouseEvent) {
    event.preventDefault();
    this.startY = event.clientY;
    this.startHeight = this.el.nativeElement.getBoundingClientRect().height;
    this.renderer.setStyle(document.body, 'user-select', 'none');
    document.addEventListener('mousemove', this.onMouseMove);
    document.addEventListener('mouseup', this.onMouseUp);
  }

  private resize(event: MouseEvent) {
    const newHeight = Math.max(this.minHeight, this.startHeight + (event.clientY - this.startY));
    this.renderer.setStyle(this.el.nativeElement, 'height', `${newHeight}px`);
  }

  private stopResize() {
    if (!this.startY) {
      return;
    }
    this.startY = 0;
    this.renderer.removeStyle(document.body, 'user-select');
    if (this.handle) {
      this.renderer.setStyle(this.handle, 'border-bottom-color', '#ced4da');
    }
    document.removeEventListener('mousemove', this.onMouseMove);
    document.removeEventListener('mouseup', this.onMouseUp);
  }

}
