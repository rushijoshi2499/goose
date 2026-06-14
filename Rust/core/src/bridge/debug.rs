use super::{BridgeRequest, BridgeResponse, bridge_error};

pub(crate) fn dispatch_debug(request: &BridgeRequest) -> BridgeResponse {
    bridge_error(
        &request.request_id,
        "not_implemented",
        format!("debug dispatch not yet implemented: {}", request.method),
    )
}
