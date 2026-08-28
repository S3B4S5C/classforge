import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { dia } from '@joint/core';

import {
  DiagramNodeLayout,
  ProjectDocument,
} from '../../model/project';
import {
  classForgeCellNamespace,
  createUmlClassCell,
} from '../uml-class-shape';
import {
  createUmlRelationshipCell,
  resetRelationshipCell,
  selectRelationshipCell,
} from '../uml-relationship-shape';

export interface UmlClassMovedEvent {
  classId: string;
  layout: DiagramNodeLayout;
}

export interface UmlRelationshipEndpoints {
  sourceClassId: string;
  targetClassId: string;
}

export type UmlCanvasSelection =
  | {
      kind: 'class';
      id: string;
    }
  | {
      kind: 'relationship';
      id: string;
    }
  | null;

@Component({
  selector: 'app-uml-canvas',
  imports: [MatButtonModule],
  templateUrl: './uml-canvas.component.html',
  styleUrl: './uml-canvas.component.scss',
})
export class UmlCanvasComponent
  implements AfterViewInit, OnChanges, OnDestroy
{
  @Input({ required: true })
  document: ProjectDocument | null = null;

  @Input()
  requestedSelection: UmlCanvasSelection = null;

  @Input()
  validating = false;

  @Input()
  canUndo = false;

  @Input()
  canRedo = false;

  @Output()
  readonly createClassRequested =
    new EventEmitter<void>();

  @Output()
  readonly validateRequested =
    new EventEmitter<void>();

  @Output()
  readonly undoRequested =
    new EventEmitter<void>();

  @Output()
  readonly redoRequested =
    new EventEmitter<void>();

  @Output()
  readonly editClassRequested =
    new EventEmitter<string>();

  @Output()
  readonly editRelationshipRequested =
    new EventEmitter<string>();

  @Output()
  readonly relationshipRequested =
    new EventEmitter<UmlRelationshipEndpoints>();

  @Output()
  readonly selectionChanged =
    new EventEmitter<UmlCanvasSelection>();

  @Output()
  readonly classMoved =
    new EventEmitter<UmlClassMovedEvent>();

  @ViewChild('paperHost', { static: true })
  private readonly paperHost!: ElementRef<HTMLDivElement>;

  @ViewChild('viewport', { static: true })
  private readonly viewport!: ElementRef<HTMLDivElement>;

  readonly zoomPercent = signal(100);
  readonly relationshipMode = signal(false);
  readonly relationshipSourceId =
    signal<string | null>(null);

  private readonly graph = new dia.Graph(
    {},
    {
      cellNamespace:
        classForgeCellNamespace,
    },
  );

  private paper: dia.Paper | null = null;
  private selection: UmlCanvasSelection = null;
  private initialized = false;
  private previousClassCount = 0;

  private panStart:
    | {
        clientX: number;
        clientY: number;
        tx: number;
        ty: number;
      }
    | null = null;

  private readonly wheelHandler = (
    event: WheelEvent,
  ): void => {
    if (!this.paper) {
      return;
    }

    event.preventDefault();

    if (
      event.ctrlKey
      || event.metaKey
    ) {
      const current =
        this.paper.scale().sx;

      const factor =
        event.deltaY < 0
          ? 1.08
          : 0.92;

      const next =
        this.clampScale(
          current * factor,
        );

      const point =
        this.paper.clientToLocalPoint(
          event.clientX,
          event.clientY,
        );

      this.paper.scaleUniformAtPoint(
        next,
        point,
      );

      return;
    }

    const translation =
      this.paper.translate();

    this.paper.translate(
      translation.tx - event.deltaX,
      translation.ty - event.deltaY,
    );
  };

  @HostListener(
    'window:keydown',
    ['$event'],
  )
  handleHistoryShortcut(
    event: KeyboardEvent,
  ): void {
    if (
      !(
        event.ctrlKey
        || event.metaKey
      )
      || this.isEditableTarget(
        event.target,
      )
    ) {
      return;
    }

    const key =
      event.key.toLowerCase();

    if (
      key === 'z'
      && event.shiftKey
      && this.canRedo
    ) {
      event.preventDefault();
      this.redoRequested.emit();
      return;
    }

    if (
      key === 'z'
      && !event.shiftKey
      && this.canUndo
    ) {
      event.preventDefault();
      this.undoRequested.emit();
      return;
    }

    if (
      key === 'y'
      && this.canRedo
    ) {
      event.preventDefault();
      this.redoRequested.emit();
    }
  }
  ngAfterViewInit(): void {
    this.paper = new dia.Paper({
      el: this.paperHost.nativeElement,
      model: this.graph,
      width: '100%',
      height: '100%',
      gridSize: 20,
      drawGrid: {
        name: 'mesh',
        args: {
          color: '#e4e7ec',
          thickness: 1,
        },
      },
      background: {
        color: '#fcfcfd',
      },
      cellViewNamespace:
        classForgeCellNamespace,
      interactive: true,
      async: false,
      sorting:
        dia.Paper.sorting.APPROX,
      labelsLayer: true,
    });

    this.bindPaperEvents();

    this.viewport.nativeElement
      .addEventListener(
        'wheel',
        this.wheelHandler,
        {
          passive: false,
        },
      );

    this.initialized = true;
    this.syncGraph(true);
  }

  ngOnChanges(
    changes: SimpleChanges,
  ): void {
    if (!this.initialized) {
      return;
    }

    if (changes['document']) {
      this.syncGraph(false);
    }

    if (changes['requestedSelection']) {
      this.applyRequestedSelection(
        this.requestedSelection,
      );
    }
  }

  ngOnDestroy(): void {
    this.viewport.nativeElement
      .removeEventListener(
        'wheel',
        this.wheelHandler,
      );

    this.graph.off();

    if (this.paper) {
      this.paper.off();
      this.paper.remove();
      this.paper = null;
    }
  }

  startRelationshipMode(): void {
    if (
      !this.document
      || this.document.umlModel.classes.length
        === 0
    ) {
      return;
    }

    this.relationshipMode.set(true);
    this.relationshipSourceId.set(null);
    this.clearSelection();
  }

  cancelRelationshipMode(): void {
    this.relationshipMode.set(false);
    this.relationshipSourceId.set(null);
    this.clearEndpointHighlights();
  }

  zoomIn(): void {
    this.zoomBy(1.15);
  }

  zoomOut(): void {
    this.zoomBy(0.87);
  }

  resetView(): void {
    if (!this.paper) {
      return;
    }

    this.paper.scale(1);
    this.paper.translate(0, 0);
  }

  fitToContent(): void {
    if (
      !this.paper
      || this.graph
        .getElements()
        .length === 0
    ) {
      this.resetView();
      return;
    }

    this.paper.transformToFitContent({
      padding: 60,
      horizontalAlign: 'middle',
      verticalAlign: 'middle',
    });

    const current =
      this.paper.scale().sx;

    if (current > 1.25) {
      this.paper.scale(1.25);
    } else if (current < 0.4) {
      this.paper.scale(0.4);
    }
  }

  private bindPaperEvents(): void {
    if (!this.paper) {
      return;
    }

    this.paper.on(
      'scale',
      (sx: number) => {
        this.zoomPercent.set(
          Math.round(sx * 100),
        );
      },
    );

    this.paper.on(
      'element:pointerclick',
      (
        elementView: dia.ElementView,
        event: dia.Event,
      ) => {
        event.stopPropagation();

        const classId =
          String(elementView.model.id);

        if (this.relationshipMode()) {
          this.handleRelationshipClassClick(
            classId,
          );
          return;
        }

        this.selectCell({
          kind: 'class',
          id: classId,
        });
      },
    );

    this.paper.on(
      'element:pointerdblclick',
      (
        elementView: dia.ElementView,
        event: dia.Event,
      ) => {
        if (this.relationshipMode()) {
          return;
        }

        event.preventDefault();
        event.stopPropagation();

        const classId =
          String(elementView.model.id);

        this.selectCell({
          kind: 'class',
          id: classId,
        });

        this.editClassRequested.emit(
          classId,
        );
      },
    );

    this.paper.on(
      'element:pointerup',
      (
        elementView: dia.ElementView,
      ) => {
        if (this.relationshipMode()) {
          return;
        }

        const classId =
          String(elementView.model.id);

        const position =
          elementView.model.position();

        const size =
          elementView.model.size();

        this.classMoved.emit({
          classId,
          layout: {
            x: Math.round(position.x),
            y: Math.round(position.y),
            width: Math.round(size.width),
            height: Math.round(size.height),
          },
        });
      },
    );

    this.paper.on(
      'link:pointerclick',
      (
        linkView: dia.LinkView,
        event: dia.Event,
      ) => {
        if (this.relationshipMode()) {
          return;
        }

        event.stopPropagation();

        this.selectCell({
          kind: 'relationship',
          id: String(linkView.model.id),
        });
      },
    );

    this.paper.on(
      'link:pointerdblclick',
      (
        linkView: dia.LinkView,
        event: dia.Event,
      ) => {
        if (this.relationshipMode()) {
          return;
        }

        event.preventDefault();
        event.stopPropagation();

        const relationshipId =
          String(linkView.model.id);

        this.selectCell({
          kind: 'relationship',
          id: relationshipId,
        });

        this.editRelationshipRequested.emit(
          relationshipId,
        );
      },
    );

    this.paper.on(
      'blank:pointerclick',
      () => {
        if (!this.relationshipMode()) {
          this.clearSelection();
        }
      },
    );

    this.paper.on(
      'blank:pointerdown',
      (event: any) => {
        const translation =
          this.paper?.translate();

        if (!translation) {
          return;
        }

        this.panStart = {
          clientX:
            event.clientX
            ?? event.pageX
            ?? 0,
          clientY:
            event.clientY
            ?? event.pageY
            ?? 0,
          tx: translation.tx,
          ty: translation.ty,
        };

        this.viewport.nativeElement
          .classList.add(
            'is-panning',
          );
      },
    );

    this.paper.on(
      'blank:pointermove',
      (event: any) => {
        if (
          !this.paper
          || !this.panStart
        ) {
          return;
        }

        const clientX =
          event.clientX
          ?? event.pageX
          ?? 0;

        const clientY =
          event.clientY
          ?? event.pageY
          ?? 0;

        this.paper.translate(
          this.panStart.tx
            + clientX
            - this.panStart.clientX,
          this.panStart.ty
            + clientY
            - this.panStart.clientY,
        );
      },
    );

    this.paper.on(
      'blank:pointerup',
      () => {
        this.panStart = null;

        this.viewport.nativeElement
          .classList.remove(
            'is-panning',
          );
      },
    );

    this.paper.on(
      'paper:pinch',
      (
        event: dia.Event,
        x: number,
        y: number,
        scale: number,
      ) => {
        if (!this.paper) {
          return;
        }

        event.preventDefault();

        const current =
          this.paper.scale().sx;

        this.paper.scaleUniformAtPoint(
          this.clampScale(
            current * scale,
          ),
          { x, y },
        );
      },
    );

    this.paper.on(
      'paper:pan',
      (
        event: dia.Event,
        deltaX: number,
        deltaY: number,
      ) => {
        if (!this.paper) {
          return;
        }

        event.preventDefault();
        event.stopPropagation();

        const translation =
          this.paper.translate();

        this.paper.translate(
          translation.tx - deltaX,
          translation.ty - deltaY,
        );
      },
    );
  }

  private handleRelationshipClassClick(
    classId: string,
  ): void {
    const sourceId =
      this.relationshipSourceId();

    if (!sourceId) {
      this.relationshipSourceId.set(
        classId,
      );

      this.highlightRelationshipSource(
        classId,
      );

      return;
    }

    this.relationshipRequested.emit({
      sourceClassId: sourceId,
      targetClassId: classId,
    });

    this.relationshipMode.set(false);
    this.relationshipSourceId.set(null);
    this.clearEndpointHighlights();
  }

  private syncGraph(
    initial: boolean,
  ): void {
    const document =
      this.document;

    if (!document) {
      this.graph.resetCells([]);
      this.previousClassCount = 0;
      return;
    }

    const elementCells =
      document.umlModel.classes.map(
        (umlClass, index) => {
          const layout =
            document.layout.nodes[
              umlClass.id
            ]
            ?? this.fallbackLayout(
              index,
            );

          return createUmlClassCell(
            umlClass,
            layout,
          );
        },
      );

    const relationshipCells =
      document.umlModel.relationships.map(
        createUmlRelationshipCell,
      );

    const oldCount =
      this.previousClassCount;

    const currentSelection =
      this.selection;

    this.graph.resetCells([
      ...elementCells,
      ...relationshipCells,
    ]);

    this.previousClassCount =
      document.umlModel.classes.length;

    if (
      currentSelection
      && this.graph.getCell(
        currentSelection.id,
      )
    ) {
      this.selectCell(
        currentSelection,
        false,
      );
    } else {
      this.selection = null;
      this.selectionChanged.emit(null);
    }

    if (
      this.relationshipMode()
      && this.relationshipSourceId()
    ) {
      this.highlightRelationshipSource(
        this.relationshipSourceId()!,
      );
    }

    if (
      this.paper
      && (
        initial
        || (
          oldCount === 0
          && elementCells.length > 0
        )
      )
    ) {
      requestAnimationFrame(
        () => this.fitToContent(),
      );
    }
  }

  private applyRequestedSelection(
    selection: UmlCanvasSelection,
  ): void {
    if (!selection) {
      this.clearSelection(false);
      return;
    }

    if (
      this.selection?.kind === selection.kind
      && this.selection.id === selection.id
    ) {
      return;
    }

    if (!this.graph.getCell(selection.id)) {
      return;
    }

    this.selectCell(
      selection,
      false,
    );
  }

  private selectCell(
    selection: Exclude<
      UmlCanvasSelection,
      null
    >,
    emit = true,
  ): void {
    this.clearSelection(false);
    this.selection = selection;

    const cell =
      this.graph.getCell(
        selection.id,
      );

    if (!cell) {
      return;
    }

    if (cell.isElement()) {
      cell.attr({
        body: {
          stroke: '#4f46e5',
          strokeWidth: 2.6,
        },
      });

      cell.toFront();
    } else if (cell.isLink()) {
      selectRelationshipCell(
        cell as dia.Link,
      );
    }

    if (emit) {
      this.selectionChanged.emit(
        selection,
      );
    }
  }

  private clearSelection(
    emit = true,
  ): void {
    if (!this.selection) {
      if (emit) {
        this.selectionChanged.emit(null);
      }

      return;
    }

    const previous =
      this.graph.getCell(
        this.selection.id,
      );

    if (previous?.isElement()) {
      previous.attr({
        body: {
          stroke: '#98a2b3',
          strokeWidth: 1.4,
        },
      });
    } else if (previous?.isLink()) {
      resetRelationshipCell(
        previous as dia.Link,
      );
    }

    this.selection = null;

    if (emit) {
      this.selectionChanged.emit(null);
    }
  }

  private highlightRelationshipSource(
    classId: string,
  ): void {
    this.clearEndpointHighlights();

    const cell =
      this.graph.getCell(classId);

    if (!cell?.isElement()) {
      return;
    }

    cell.attr({
      body: {
        stroke: '#d97706',
        strokeWidth: 3,
      },
    });

    cell.toFront();
  }

  private clearEndpointHighlights(): void {
    for (
      const element
      of this.graph.getElements()
    ) {
      const isSelected =
        this.selection?.kind === 'class'
        && this.selection.id
          === String(element.id);

      element.attr({
        body: {
          stroke:
            isSelected
              ? '#4f46e5'
              : '#98a2b3',
          strokeWidth:
            isSelected
              ? 2.6
              : 1.4,
        },
      });
    }
  }

  private zoomBy(
    factor: number,
  ): void {
    if (!this.paper) {
      return;
    }

    const current =
      this.paper.scale().sx;

    const next =
      this.clampScale(
        current * factor,
      );

    const rect =
      this.viewport.nativeElement
        .getBoundingClientRect();

    const center =
      this.paper.clientToLocalPoint(
        rect.left + rect.width / 2,
        rect.top + rect.height / 2,
      );

    this.paper.scaleUniformAtPoint(
      next,
      center,
    );
  }

  private isEditableTarget(
    target: EventTarget | null,
  ): boolean {
    if (!(target instanceof HTMLElement)) {
      return false;
    }

    return Boolean(
      target.closest(
        'input, textarea, select, [contenteditable="true"], [role="dialog"]',
      ),
    );
  }
  private clampScale(
    value: number,
  ): number {
    return Math.min(
      2,
      Math.max(0.4, value),
    );
  }

  private fallbackLayout(
    index: number,
  ): DiagramNodeLayout {
    const column =
      index % 3;

    const row =
      Math.floor(index / 3);

    return {
      x: 80 + column * 300,
      y: 80 + row * 220,
      width: 260,
      height: 160,
    };
  }
}