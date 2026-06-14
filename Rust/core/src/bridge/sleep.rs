use super::{BridgeRequest, BridgeResponse, bridge_error};

pub(crate) fn dispatch_sleep(request: &BridgeRequest) -> BridgeResponse {
    bridge_error(
        &request.request_id,
        "not_implemented",
        format!("sleep dispatch not yet implemented: {}", request.method),
    )
}
