use crate::{
    App,
    error::{Error, Result},
};
use axum::{
    Json,
    extract::{FromRequestParts, State},
    http::{StatusCode, request::Parts},
};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

pub struct Actor(pub String);
impl<S: Send + Sync> FromRequestParts<S> for Actor {
    type Rejection = Error;
    async fn from_request_parts(parts: &mut Parts, _: &S) -> Result<Self> {
        let id = parts
            .headers
            .get("x-account-id")
            .and_then(|v| v.to_str().ok());
        match id {
            Some("xiaobai" | "xiaojimao") => Ok(Self(id.unwrap().into())),
            _ => Err(Error(StatusCode::UNAUTHORIZED, "请选择小白或小鸡毛")),
        }
    }
}

#[derive(Serialize, FromRow)]
pub struct Account {
    pub id: String,
    pub name: String,
    pub avatar_id: Option<Uuid>,
    pub cover_id: Option<Uuid>,
}
pub async fn list(State(app): State<App>) -> Result<Json<Vec<Account>>> {
    Ok(Json(
        sqlx::query_as("SELECT * FROM accounts ORDER BY id")
            .fetch_all(&app.db)
            .await?,
    ))
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Profile {
    pub avatar_id: Option<Uuid>,
    pub cover_id: Option<Uuid>,
}

pub async fn update(
    State(app): State<App>,
    Actor(actor): Actor,
    Json(input): Json<Profile>,
) -> Result<Json<Account>> {
    let mut tx = app.db.begin().await?;
    for id in [input.avatar_id, input.cover_id].into_iter().flatten() {
        crate::media::require_owned(&mut tx, &actor, id).await?;
    }
    let account = sqlx::query_as("UPDATE accounts SET avatar_id = COALESCE($2, avatar_id), cover_id = COALESCE($3, cover_id) WHERE id = $1 RETURNING *")
        .bind(actor).bind(input.avatar_id).bind(input.cover_id).fetch_one(&mut *tx).await?;
    tx.commit().await?;
    Ok(Json(account))
}
