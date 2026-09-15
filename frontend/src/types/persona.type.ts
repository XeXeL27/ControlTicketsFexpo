// Refleja PersonaDto — entrada para crear/actualizar.
export interface PersonaDto {
  nombre: string
  paterno: string
  materno?: string
  ci: string
  genero?: '' | 'MASCULINO' | 'FEMENINO' | 'OTRO'
}

// Refleja PersonaDetalleDto — respuesta del backend.
export interface PersonaDetalleDto {
  idPersona: number
  nombre: string
  paterno: string
  materno?: string
  nombreCompleto: string
  ci: string
  genero?: string
  estado: string
}
