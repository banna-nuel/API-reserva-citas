package com.q.q.dto.response;

// Lo que devuelven las lecturas de /professionals según quién pregunta. Son dos tipos distintos (y no
// campos que se ocultan) para que un dato solo-ADMIN no pueda colarse en la vista pública por accidente
public sealed interface ProfessionalView permits ProfessionalResponse, ProfessionalAdminResponse {}
