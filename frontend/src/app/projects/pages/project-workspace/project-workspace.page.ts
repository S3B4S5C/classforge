import { DatePipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import {
  FormControl,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';

import {
  AttributeDialogComponent,
  AttributeDialogResult,
} from '../../dialogs/attribute-dialog/attribute-dialog.component';
import {
  ClassDialogComponent,
} from '../../dialogs/class-dialog/class-dialog.component';
import {
  ConfirmDialogComponent,
} from '../../dialogs/confirm-dialog/confirm-dialog.component';
import {
  CollaboratorsDialogComponent,
} from '../../dialogs/collaborators-dialog/collaborators-dialog.component';
import {
  RelationshipDialogComponent,
  RelationshipDialogResult,
} from '../../dialogs/relationship-dialog/relationship-dialog.component';
import {
  UmlCanvasComponent,
  UmlCanvasCursorEvent,
  UmlCanvasSelection,
  UmlClassMovedEvent,
  UmlRelationshipEndpoints,
} from '../../diagram/uml-canvas/uml-canvas.component';
import {
  formatMultiplicity,
  relationshipTypeLabel,
} from '../../diagram/uml-relationship-shape';
import {
  ProjectValidationDiagnostic,
  UmlAttribute,
  UmlClass,
  UmlDataType,
  UmlRelationship,
  UmlVisibility,
} from '../../model/project';
import {
  ProjectSaveState,
  ProjectWorkspaceStore,
} from '../../state/project-workspace.store';
import {
  ProjectAssistantPanelComponent,
} from '../../assistant/project-assistant-panel.component';
import {
  ProjectImageUmlPanelComponent,
} from '../../assistant/project-image-uml-panel.component';
import {
  ProjectPresenceService,
} from '../../presence/project-presence.service';

@Component({
  selector: 'app-project-workspace-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
    ProjectAssistantPanelComponent,
    ProjectImageUmlPanelComponent,
    UmlCanvasComponent,
  ],
  providers: [ProjectWorkspaceStore],
  templateUrl: './project-workspace.page.html',
  styleUrl: './project-workspace.page.scss',
})
export class ProjectWorkspacePage {
  private readonly route =
    inject(ActivatedRoute);

  private readonly dialog =
    inject(MatDialog);

  private readonly destroyRef =
    inject(DestroyRef);

  readonly presence =
    inject(ProjectPresenceService);

  readonly store =
    inject(ProjectWorkspaceStore);

  readonly editingName = signal(false);

  readonly classesExpanded =
    signal(false);

  readonly relationshipsExpanded =
    signal(false);

  readonly diagramSelection =
    signal<UmlCanvasSelection>(null);

  readonly layoutNodeCount = computed(
    () =>
      Object.keys(
        this.store.documentDraft()?.layout.nodes ?? {},
      ).length,
  );

  readonly totalAttributeCount = computed(
    () =>
      this.store.classes().reduce(
        (total, umlClass) =>
          total + umlClass.attributes.length,
        0,
      ),
  );

  readonly selectedClass = computed(
    () => {
      const selection =
        this.diagramSelection();

      if (
        selection?.kind !== 'class'
      ) {
        return null;
      }

      return (
        this.store.classes().find(
          (umlClass) =>
            umlClass.id === selection.id,
        )
        ?? null
      );
    },
  );

  readonly selectedRelationship = computed(
    () => {
      const selection =
        this.diagramSelection();

      if (
        selection?.kind
          !== 'relationship'
      ) {
        return null;
      }

      return (
        this.store.relationships().find(
          (relationship) =>
            relationship.id
              === selection.id,
        )
        ?? null
      );
    },
  );

  readonly nameControl =
    new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(120),
      ],
    });

  constructor() {
    this.destroyRef.onDestroy(
      () =>
        this.presence.detachProject(),
    );

    const projectId =
      this.route.snapshot.paramMap.get('id');

    if (projectId) {
      this.store.load(projectId);
    }

    effect(() => {
      const project =
        this.store.project();

      if (project) {
        this.presence.attachProject(
          project.id,
        );
      }
    });

    effect(() => {
      const project =
        this.store.project();

      if (
        project
        && !this.editingName()
      ) {
        this.nameControl.setValue(
          project.name,
          { emitEvent: false },
        );
      }
    });

    effect(() => {
      const selection =
        this.diagramSelection();

      if (!selection) {
        return;
      }

      const exists =
        selection.kind === 'class'
          ? this.store.classes().some(
              (umlClass) =>
                umlClass.id
                  === selection.id,
            )
          : this.store.relationships().some(
              (relationship) =>
                relationship.id
                  === selection.id,
            );

      if (!exists) {
        this.diagramSelection.set(null);
      }
    });
  }

  openCollaborators(): void {
    const project = this.store.project();

    if (!project) {
      return;
    }

    this.dialog.open(
      CollaboratorsDialogComponent,
      {
        data: {
          projectId: project.id,
          projectName: project.name,
          accessRole: project.accessRole,
        },
        width: '720px',
        maxWidth: '94vw',
      },
    );
  }

  retry(): void {
    const projectId =
      this.route.snapshot.paramMap.get('id');

    if (projectId) {
      this.store.load(projectId);
    }
  }

  startRename(): void {
    const project =
      this.store.project();

    if (!project) {
      return;
    }

    this.nameControl.setValue(
      project.name,
    );

    this.editingName.set(true);
  }

  cancelRename(): void {
    const project =
      this.store.project();

    if (project) {
      this.nameControl.setValue(
        project.name,
      );
    }

    this.editingName.set(false);
  }

  rename(): void {
    this.nameControl.markAsTouched();

    const name =
      this.nameControl.value.trim();

    if (
      !name
      || this.nameControl.invalid
      || this.store.renaming()
    ) {
      return;
    }

    this.store.rename(name);
    this.editingName.set(false);
  }

  save(): void {
    this.store.saveDocument();
  }

  validateModel(): void {
    this.store.validateDocument();
  }

  selectValidationDiagnostic(
    diagnostic: ProjectValidationDiagnostic,
  ): void {
    if (!diagnostic.elementId) {
      return;
    }

    const classExists =
      this.store.classes().some(
        (umlClass) =>
          umlClass.id === diagnostic.elementId,
      );

    if (classExists) {
      this.diagramSelection.set({
        kind: 'class',
        id: diagnostic.elementId,
      });
      return;
    }

    const relationshipExists =
      this.store.relationships().some(
        (relationship) =>
          relationship.id === diagnostic.elementId,
      );

    if (relationshipExists) {
      this.diagramSelection.set({
        kind: 'relationship',
        id: diagnostic.elementId,
      });
    }
  }

  validationSeverityLabel(
    diagnostic: ProjectValidationDiagnostic,
  ): string {
    switch (diagnostic.severity) {
      case 'ERROR':
        return 'Error';
      case 'WARNING':
        return 'Advertencia';
      default:
        return 'Info';
    }
  }

  validationSeverityIcon(
    diagnostic: ProjectValidationDiagnostic,
  ): string {
    switch (diagnostic.severity) {
      case 'ERROR':
        return 'error';
      case 'WARNING':
        return 'warning';
      default:
        return 'info';
    }
  }

  createClass(): void {
    const reservedNames =
      this.store.classes().map(
        (umlClass) => umlClass.name,
      );

    this.dialog
      .open(ClassDialogComponent, {
        width: '520px',
        maxWidth: '94vw',
        data: {
          mode: 'create',
          reservedNames,
        },
      })
      .afterClosed()
      .subscribe((name) => {
        if (name) {
          this.store.addClass(name);
        }
      });
  }

  editClassById(
    classId: string,
  ): void {
    const umlClass =
      this.store.classes().find(
        (item) => item.id === classId,
      );

    if (umlClass) {
      this.editClass(umlClass);
    }
  }

  editClass(
    umlClass: UmlClass,
  ): void {
    const reservedNames =
      this.store.classes().map(
        (item) => item.name,
      );

    this.dialog
      .open(ClassDialogComponent, {
        width: '520px',
        maxWidth: '94vw',
        data: {
          mode: 'edit',
          currentName: umlClass.name,
          reservedNames,
        },
      })
      .afterClosed()
      .subscribe((name) => {
        if (name) {
          this.store.updateClass(
            umlClass.id,
            { name },
          );
        }
      });
  }

  deleteClass(
    umlClass: UmlClass,
  ): void {
    const relationshipCount =
      this.store.relationships().filter(
        (relationship) =>
          relationship.sourceClassId
            === umlClass.id
          || relationship.targetClassId
            === umlClass.id,
      ).length;

    const relationMessage =
      relationshipCount > 0
        ? ` Tambien se eliminaran ${relationshipCount} relaciones asociadas.`
        : '';

    this.dialog
      .open(ConfirmDialogComponent, {
        width: '500px',
        maxWidth: '94vw',
        data: {
          title:
            `Eliminar ${umlClass.name}`,
          message:
            `Se eliminara la clase y todos sus atributos.${relationMessage} Esta accion se aplicara al guardar el documento.`,
          confirmLabel:
            'Eliminar clase',
          icon: 'delete',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.store.removeClass(
            umlClass.id,
          );
        }
      });
  }

  moveClass(
    event: UmlClassMovedEvent,
  ): void {
    this.store.updateNodeLayout(
      event.classId,
      event.layout,
    );
  }

  selectDiagramElement(
    selection: UmlCanvasSelection,
  ): void {
    this.diagramSelection.set(
      selection,
    );

    this.presence.selectElement(
      selection,
    );
  }

  movePresenceCursor(
    cursor: UmlCanvasCursorEvent,
  ): void {
    this.presence.moveCursor(
      cursor,
    );
  }

  createRelationshipFromCanvas(
    endpoints: UmlRelationshipEndpoints,
  ): void {
    this.openRelationshipDialog({
      mode: 'create',
      sourceClassId:
        endpoints.sourceClassId,
      targetClassId:
        endpoints.targetClassId,
    });
  }

  editRelationshipById(
    relationshipId: string,
  ): void {
    const relationship =
      this.store.relationships().find(
        (item) =>
          item.id === relationshipId,
      );

    if (relationship) {
      this.editRelationship(
        relationship,
      );
    }
  }

  editRelationship(
    relationship: UmlRelationship,
  ): void {
    this.openRelationshipDialog({
      mode: 'edit',
      relationship,
    });
  }

  deleteRelationship(
    relationship: UmlRelationship,
  ): void {
    const sourceName =
      this.className(
        relationship.sourceClassId,
      );

    const targetName =
      this.className(
        relationship.targetClassId,
      );

    this.dialog
      .open(ConfirmDialogComponent, {
        width: '500px',
        maxWidth: '94vw',
        data: {
          title: 'Eliminar relacion',
          message:
            `Se eliminara la ${this.relationshipTypeLabel(relationship)} entre ${sourceName} y ${targetName}. El cambio sera persistente al guardar.`,
          confirmLabel:
            'Eliminar relacion',
          icon: 'link_off',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.store.removeRelationship(
            relationship.id,
          );

          if (
            this.diagramSelection()?.kind
              === 'relationship'
            && this.diagramSelection()?.id
              === relationship.id
          ) {
            this.diagramSelection.set(null);
          }
        }
      });
  }

  addAttribute(
    umlClass: UmlClass,
  ): void {
    this.dialog
      .open(AttributeDialogComponent, {
        width: '580px',
        maxWidth: '94vw',
        data: {
          mode: 'create',
          className: umlClass.name,
          reservedNames:
            umlClass.attributes.map(
              (attribute) =>
                attribute.name,
            ),
        },
      })
      .afterClosed()
      .subscribe(
        (
          result:
            | AttributeDialogResult
            | undefined,
        ) => {
          if (result) {
            this.store.addAttribute(
              umlClass.id,
              result,
            );
          }
        },
      );
  }

  editAttribute(
    umlClass: UmlClass,
    attribute: UmlAttribute,
  ): void {
    this.dialog
      .open(AttributeDialogComponent, {
        width: '580px',
        maxWidth: '94vw',
        data: {
          mode: 'edit',
          className: umlClass.name,
          attribute,
          reservedNames:
            umlClass.attributes.map(
              (item) => item.name,
            ),
        },
      })
      .afterClosed()
      .subscribe(
        (
          result:
            | AttributeDialogResult
            | undefined,
        ) => {
          if (result) {
            this.store.updateAttribute(
              umlClass.id,
              attribute.id,
              result,
            );
          }
        },
      );
  }

  deleteAttribute(
    umlClass: UmlClass,
    attribute: UmlAttribute,
  ): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        width: '480px',
        maxWidth: '94vw',
        data: {
          title:
            `Eliminar ${attribute.name}`,
          message:
            `Se eliminara el atributo de ${umlClass.name}. El cambio se hara persistente al guardar el documento.`,
          confirmLabel:
            'Eliminar atributo',
          icon: 'delete',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.store.removeAttribute(
            umlClass.id,
            attribute.id,
          );
        }
      });
  }

  className(
    classId: string,
  ): string {
    return (
      this.store.classes().find(
        (umlClass) =>
          umlClass.id === classId,
      )?.name
      ?? 'Clase desconocida'
    );
  }

  relationshipTypeLabel(
    relationship: UmlRelationship,
  ): string {
    return relationshipTypeLabel(
      relationship.type,
    );
  }

  multiplicityLabel(
    relationship: UmlRelationship,
    side: 'source' | 'target',
  ): string {
    if (
      relationship.type
        === 'GENERALIZATION'
    ) {
      return '—';
    }

    return formatMultiplicity(
      side === 'source'
        ? relationship.sourceMultiplicity
        : relationship.targetMultiplicity,
    );
  }

  visibilitySymbol(
    visibility: UmlVisibility,
  ): string {
    switch (visibility) {
      case 'PUBLIC':
        return '+';
      case 'PROTECTED':
        return '#';
      case 'PACKAGE':
        return '~';
      default:
        return '-';
    }
  }

  dataTypeLabel(
    attribute: UmlAttribute,
  ): string {
    if (
      attribute.dataType === 'CUSTOM'
      && attribute.customTypeName
    ) {
      return attribute.customTypeName;
    }

    const labels:
      Record<UmlDataType, string> = {
        STRING: 'String',
        INTEGER: 'Integer',
        LONG: 'Long',
        DECIMAL: 'Decimal',
        BOOLEAN: 'Boolean',
        DATE: 'Date',
        DATETIME: 'DateTime',
        UUID: 'UUID',
        CUSTOM: 'Custom',
      };

    return labels[attribute.dataType];
  }

  saveStateIcon(
    state: ProjectSaveState,
  ): string {
    switch (state) {
      case 'dirty':
        return 'edit';
      case 'saving':
        return 'sync';
      case 'conflict':
        return 'warning';
      case 'error':
        return 'error';
      default:
        return 'check_circle';
    }
  }

  saveStateText(
    state: ProjectSaveState,
  ): string {
    switch (state) {
      case 'dirty':
        return 'Cambios sin guardar';
      case 'saving':
        return 'Guardando...';
      case 'conflict':
        return 'Conflicto de revision';
      case 'error':
        return 'Error de guardado';
      default:
        return 'Guardado';
    }
  }

  private openRelationshipDialog(
    options:
      | {
          mode: 'create';
          sourceClassId: string;
          targetClassId: string;
        }
      | {
          mode: 'edit';
          relationship: UmlRelationship;
        },
  ): void {
    this.dialog
      .open(RelationshipDialogComponent, {
        width: '640px',
        maxWidth: '94vw',
        data: {
          ...options,
          classes: this.store.classes(),
        },
      })
      .afterClosed()
      .subscribe(
        (
          result:
            | RelationshipDialogResult
            | undefined,
        ) => {
          if (!result) {
            return;
          }

          if (options.mode === 'create') {
            this.store.addRelationship(
              result,
            );
          } else {
            this.store.updateRelationship(
              options.relationship.id,
              result,
            );
          }
        },
      );
  }
}
