use super::{BridgeRequest, BridgeResponse, bridge_error};

pub(crate) fn dispatch_activity(request: &BridgeRequest) -> BridgeResponse {
    bridge_error(
        &request.request_id,
        "not_implemented",
        format!("activity dispatch not yet implemented: {}", request.method),
    )
}
