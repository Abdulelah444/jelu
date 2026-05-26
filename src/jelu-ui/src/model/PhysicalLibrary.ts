export interface PhysicalLocation {
    id?: string,
    name: string,
    creationDate?: string,
    modificationDate?: string,
}

export interface CreatePhysicalLocation {
    name: string,
}

export interface PhysicalBookcase {
    id?: string,
    name: string,
    locationId: string,
    locationName?: string,
    shelfCount: number,
    description?: string,
    sortOrder: number,
    creationDate?: string,
    modificationDate?: string,
    shelves?: Array<PhysicalShelf>,
}

export interface CreatePhysicalBookcase {
    name: string,
    shelfCount: number,
    description?: string,
    sortOrder?: number,
}

export interface UpdatePhysicalBookcase {
    name?: string,
    shelfCount?: number,
    description?: string,
    sortOrder?: number,
}

export interface PhysicalShelf {
    id?: string,
    bookcaseId: string,
    bookcaseName?: string,
    position: number,
    label?: string,
    tagId?: string,
    tagName?: string,
    creationDate?: string,
    modificationDate?: string,
}

export interface UpdatePhysicalShelf {
    label?: string,
    tagId?: string,
}

export interface PhysicalShelfBook {
    id?: string,
    shelfId: string,
    userBookId: string,
    position: number,
}

export interface AssignBookToShelf {
    userBookId: string,
    position?: number,
}

export interface BulkAssignBooksToShelf {
    userBookIds: Array<string>,
}

export interface ShelfLocation {
    bookcaseNumber: number,
    locationName: string,
    bookcaseName: string,
    shelfLabel: string,
    shelfPosition: number,
    displayString: string,
}
